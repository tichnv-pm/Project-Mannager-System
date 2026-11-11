import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DragDropModule, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { TaskService } from '../../tasks/task.service';
import { TaskSummaryResponse } from '../../tasks/task.model';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { EmptyStateComponent } from '../../../shared/components/empty-state.component';

const UNASSIGNED_KEY = 'none';

@Component({
  selector: 'app-backlog-board',
  standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule, HasPermissionDirective, EmptyStateComponent],
  templateUrl: './backlog-board.component.html',
  styleUrls: ['./backlog-board.component.scss']
})
export class BacklogBoardComponent implements OnInit {
  @Input() projectId!: string;

  private taskService = inject(TaskService);

  readonly UNASSIGNED_KEY = UNASSIGNED_KEY;

  loading = signal(true);
  error = signal<string | null>(null);
  busy = signal(false);

  epics = signal<TaskSummaryResponse[]>([]);
  childrenMap = signal<Record<string, TaskSummaryResponse[]>>({});

  // "+ thêm" nhanh (epic mới hoặc story dưới 1 cột)
  addingEpic = signal(false);
  newEpicTitle = signal('');
  addingStoryFor = signal<string | null>(null);
  newStoryTitle = signal('');

  ngOnInit(): void {
    if (this.projectId) {
      this.load();
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.taskService.getTasks({ projectId: this.projectId, size: 500, sort: 'createdAt,asc' }).subscribe({
      next: (res) => {
        const all = res.content || [];
        const epics = all.filter(t => t.type === 'EPIC');
        const epicIds = new Set(epics.map(e => e.id));
        const map: Record<string, TaskSummaryResponse[]> = { [UNASSIGNED_KEY]: [] };
        epics.forEach(e => (map[e.id] = []));
        for (const t of all) {
          if (t.type === 'EPIC') continue;
          const key = t.parentTaskId && epicIds.has(t.parentTaskId) ? t.parentTaskId : UNASSIGNED_KEY;
          map[key].push(t);
        }
        this.epics.set(epics);
        this.childrenMap.set(map);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message || 'Không thể tải Backlog');
        this.loading.set(false);
      }
    });
  }

  columnTasks(key: string): TaskSummaryResponse[] {
    return this.childrenMap()[key] || [];
  }

  columnStoryPoints(key: string): number {
    return this.columnTasks(key).reduce((sum, t) => sum + (t.storyPoints || 0), 0);
  }

  onDrop(event: CdkDragDrop<TaskSummaryResponse[]>, targetKey: string): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      return;
    }
    const task = event.previousContainer.data[event.previousIndex];
    transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);

    this.busy.set(true);
    this.taskService.changeParent(task.id, targetKey === UNASSIGNED_KEY ? null : targetKey).subscribe({
      next: (updated) => {
        task.parentTaskId = updated.parentTaskId;
        this.busy.set(false);
      },
      error: (err) => {
        alert(err.message || 'Không thể chuyển Story/Task sang Epic khác.');
        this.busy.set(false);
        this.load();
      }
    });
  }

  // ─── Quick add ────────────────────────────────────────────────────
  openAddEpic(): void {
    this.newEpicTitle.set('');
    this.addingEpic.set(true);
  }

  cancelAddEpic(): void {
    this.addingEpic.set(false);
  }

  confirmAddEpic(): void {
    const title = this.newEpicTitle().trim();
    if (!title || this.busy()) return;
    this.busy.set(true);
    this.taskService.createTask({ projectId: this.projectId, title, type: 'EPIC' }).subscribe({
      next: () => {
        this.busy.set(false);
        this.addingEpic.set(false);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        alert(err.message || 'Tạo Epic thất bại');
      }
    });
  }

  openAddStory(columnKey: string): void {
    this.newStoryTitle.set('');
    this.addingStoryFor.set(columnKey);
  }

  cancelAddStory(): void {
    this.addingStoryFor.set(null);
  }

  confirmAddStory(columnKey: string): void {
    const title = this.newStoryTitle().trim();
    if (!title || this.busy()) return;
    this.busy.set(true);
    this.taskService.createTask({
      projectId: this.projectId,
      title,
      type: 'STORY',
      ...(columnKey !== UNASSIGNED_KEY ? { parentTaskId: columnKey } : {})
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.addingStoryFor.set(null);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        alert(err.message || 'Tạo Story thất bại');
      }
    });
  }
}
