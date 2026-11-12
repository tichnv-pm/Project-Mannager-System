import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { DragDropModule, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { SprintService } from './sprint.service';
import { BurndownPointResponse, SprintResponse, SprintStatus } from './sprint.model';
import { TaskService } from '../../tasks/task.service';
import { TaskSummaryResponse } from '../../tasks/task.model';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-project-sprints',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    DragDropModule,
    HasPermissionDirective
  ],
  templateUrl: './project-sprints.component.html',
  styleUrls: ['./project-sprints.component.scss']
})
export class ProjectSprintsComponent implements OnInit {
  @Input() projectId!: string;

  private sprintService = inject(SprintService);
  private taskService = inject(TaskService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);
  
  sprints = signal<SprintResponse[]>([]);
  backlogTasks = signal<TaskSummaryResponse[]>([]);
  sprintTasksMap = signal<Record<string, TaskSummaryResponse[]>>({});
  burndown = signal<BurndownPointResponse[]>([]);

  // Sprint Creation Modal
  showCreateModal = signal(false);
  sprintForm: FormGroup;
  formSubmitting = signal(false);
  formError = signal<string | null>(null);

  constructor() {
    this.sprintForm = this.fb.group({
      sprintName: ['', [Validators.required, Validators.maxLength(100)]],
      startDate: ['', [Validators.required]],
      endDate: ['', [Validators.required]],
      goal: ['', [Validators.maxLength(500)]]
    });
  }

  ngOnInit(): void {
    if (this.projectId) {
      this.loadAllData();
    }
  }

  loadAllData(): void {
    this.loading.set(true);
    this.error.set(null);

    // Fetch Sprints first
    this.sprintService.getSprints(this.projectId).subscribe({
      next: (sprints) => {
        this.sprints.set(sprints);
        
        // Fetch Backlog Tasks (sprintId is none/null)
        this.taskService.getTasks({ projectId: this.projectId, sprintId: 'none', size: 100 }).subscribe({
          next: (backlogRes) => {
            this.backlogTasks.set(backlogRes.content || []);

            // Now fetch tasks for each sprint
            let loadedCount = 0;
            const tempMap: Record<string, TaskSummaryResponse[]> = {};

            if (sprints.length === 0) {
              this.sprintTasksMap.set({});
              this.loading.set(false);
              return;
            }

            sprints.forEach((s) => {
              this.taskService.getTasks({ projectId: this.projectId, sprintId: s.id, size: 100 }).subscribe({
                next: (sprintTasksRes) => {
                  tempMap[s.id] = sprintTasksRes.content || [];
                  loadedCount++;
                  if (loadedCount === sprints.length) {
                    this.sprintTasksMap.set(tempMap);
                    this.loading.set(false);
                    this.loadBurndownForActiveSprint(sprints);
                  }
                },
                error: (err) => {
                  this.error.set(err.message || 'Lỗi tải công việc của Sprint');
                  this.loading.set(false);
                }
              });
            });
          },
          error: (err) => {
            this.error.set(err.message || 'Lỗi tải Backlog');
            this.loading.set(false);
          }
        });
      },
      error: (err) => {
        this.error.set(err.message || 'Lỗi tải danh sách Sprint');
        this.loading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.sprintForm.reset({
      sprintName: '',
      startDate: new Date().toISOString().split('T')[0],
      endDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      goal: ''
    });
    this.formError.set(null);
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
  }

  createSprint(): void {
    if (this.sprintForm.invalid || this.formSubmitting()) {
      this.sprintForm.markAllAsTouched();
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);

    this.sprintService.createSprint(this.projectId, this.sprintForm.value).subscribe({
      next: () => {
        this.formSubmitting.set(false);
        this.closeCreateModal();
        this.loadAllData();
      },
      error: (err) => {
        this.formSubmitting.set(false);
        this.formError.set(err.message || 'Không thể tạo Sprint');
      }
    });
  }

  startSprint(sprint: SprintResponse): void {
    const activeSprintExists = this.sprints().some(s => s.status === 'ACTIVE');
    if (activeSprintExists) {
      alert('Đã có Sprint đang chạy trong dự án này. Hãy đóng Sprint hiện hành trước khi bắt đầu Sprint mới.');
      return;
    }

    if (!confirm(`Bạn có chắc chắn muốn BẮT ĐẦU Sprint "${sprint.sprintName}"?`)) {
      return;
    }

    this.sprintService.updateSprint(sprint.id, {
      sprintName: sprint.sprintName,
      startDate: sprint.startDate,
      endDate: sprint.endDate,
      goal: sprint.goal,
      status: 'ACTIVE'
    }).subscribe({
      next: () => {
        this.loadAllData();
      },
      error: (err) => {
        alert(err.message || 'Kích hoạt Sprint thất bại');
      }
    });
  }

  closeSprint(sprint: SprintResponse): void {
    if (!confirm(`Bạn có chắc muốn ĐÓNG Sprint "${sprint.sprintName}"? Toàn bộ các công việc chưa hoàn thành (DONE) sẽ tự động bị đẩy trở lại Backlog.`)) {
      return;
    }

    this.sprintService.updateSprint(sprint.id, {
      sprintName: sprint.sprintName,
      startDate: sprint.startDate,
      endDate: sprint.endDate,
      goal: sprint.goal,
      status: 'COMPLETED'
    }).subscribe({
      next: () => {
        this.loadAllData();
      },
      error: (err) => {
        alert(err.message || 'Đóng Sprint thất bại');
      }
    });
  }

  deleteSprint(sprint: SprintResponse): void {
    if (!confirm(`Bạn có chắc muốn XÓA Sprint "${sprint.sprintName}"?`)) {
      return;
    }

    this.sprintService.deleteSprint(sprint.id).subscribe({
      next: () => {
        this.loadAllData();
      },
      error: (err) => {
        alert(err.message || 'Xóa Sprint thất bại');
      }
    });
  }

  // ─── Drag and Drop ───────────────────────────────────────────────

  onDrop(event: CdkDragDrop<TaskSummaryResponse[]>, sprintId: string | null): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      const task = event.previousContainer.data[event.previousIndex];

      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex
      );

      // Đổi sprint qua endpoint riêng (PUT /tasks/{id}/sprint) — generic updateTask() ghi đè vô điều
      // kiện storyPoints/ngày/mô tả nếu không gửi lại, kéo-thả chỉ muốn đổi đúng sprintId.
      this.taskService.changeSprint(task.id, sprintId ? sprintId : null).subscribe({
        next: (updatedTask) => {
          // Update version to avoid optimistic locking error on next move
          task.version = updatedTask.version;
        },
        error: (err) => {
          alert(err.message || 'Không thể di chuyển công việc. Vui lòng tải lại trang.');
          this.loadAllData();
        }
      });
    }
  }

  getSprintTasks(sprintId: string): TaskSummaryResponse[] {
    return this.sprintTasksMap()[sprintId] || [];
  }

  getSprintVelocity(sprintId: string): { total: number; completed: number } {
    const tasks = this.getSprintTasks(sprintId);
    let total = 0;
    let completed = 0;
    for (const t of tasks) {
      const points = t.storyPoints || 0;
      total += points;
      if (t.status === 'DONE') {
        completed += points;
      }
    }
    return { total, completed };
  }

  // ─── Burndown (Sprint ACTIVE) ──────────────────────────────────────

  private loadBurndownForActiveSprint(sprints: SprintResponse[]): void {
    const active = sprints.find(s => s.status === 'ACTIVE');
    if (!active) {
      this.burndown.set([]);
      return;
    }
    this.sprintService.getBurndown(active.id).subscribe({
      next: points => this.burndown.set(points || []),
      error: () => this.burndown.set([])
    });
  }

  burndownPath(key: 'actualRemainingStoryPoints' | 'idealRemainingStoryPoints'): string {
    const points = this.burndown();
    if (points.length === 0) return '';
    const maxY = Math.max(...points.map(p => p.totalStoryPoints), 1);
    const stepX = points.length > 1 ? 300 / (points.length - 1) : 0;
    return points
      .map((p, i) => {
        const x = i * stepX;
        const y = 100 - (p[key] / maxY) * 100;
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  }
}
