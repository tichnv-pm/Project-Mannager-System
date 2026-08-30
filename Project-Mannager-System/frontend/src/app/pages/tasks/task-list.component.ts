import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { PriorityChipComponent } from '../../shared/components/priority-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { TaskService } from './task.service';
import {
  TaskFilterParams,
  TaskPriority,
  TaskStatus,
  TaskSummaryResponse,
  TaskType
} from './task.model';
import { ProjectService } from '../projects/project.service';
import { ProjectOption } from '../dashboard/dashboard.model';
import { ProjectMemberResponse } from '../projects/project.model';
import { UserBrief } from '../../core/models/auth.model';

type QuickFilter = 'all' | 'my-tasks' | 'today' | 'overdue' | 'blocked';
type ViewMode = 'table' | 'kanban';

const KANBAN_COLUMNS: { key: TaskStatus; label: string; cls: string; icon: string }[] = [
  { key: 'TODO',        label: 'Cần làm',         cls: 'todo-head',     icon: '📌' },
  { key: 'IN_PROGRESS', label: 'Đang làm',         cls: 'progress-head', icon: '🔄' },
  { key: 'REVIEW',      label: 'Đang review',      cls: 'review-head',   icon: '🔍' },
  { key: 'BLOCKED',     label: 'Tắc nghẽn',        cls: 'blocked-head',  icon: '🚧' },
  { key: 'DONE',        label: 'Hoàn thành',       cls: 'done-head',     icon: '✅' },
  { key: 'CANCELLED',   label: 'Đã hủy',           cls: 'cancelled-head',icon: '❌' },
];

@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    PageHeaderComponent,
    StatusChipComponent,
    PriorityChipComponent,
    EmptyStateComponent,
    HasPermissionDirective
  ],
  templateUrl: './task-list.component.html',
  styleUrls: ['./task-list.component.scss']
})
export class TaskListComponent implements OnInit, OnDestroy {
  private taskService = inject(TaskService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);
  private destroy$ = new Subject<void>();

  // ─── UI State ─────────────────────────────────────────────────────
  loading = signal(true);
  error = signal<string | null>(null);
  viewMode = signal<ViewMode>('table');
  quickFilter = signal<QuickFilter>('all');
  kanbanColumns = KANBAN_COLUMNS;

  // ─── Data ─────────────────────────────────────────────────────────
  tasks = signal<TaskSummaryResponse[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  projectsList = signal<ProjectOption[]>([]);
  usersList = signal<UserBrief[]>([]);
  projectMembers = signal<ProjectMemberResponse[]>([]);

  // ─── Filter State ─────────────────────────────────────────────────
  keyword = '';
  selectedProjectId = '';
  selectedAssigneeId = '';
  selectedStatuses: string[] = [];
  selectedPriorities: string[] = [];
  selectedTypes: string[] = [];
  filterOverdue = false;
  filterBlocked = false;
  dueDateFrom = '';
  dueDateTo = '';
  page = 0;
  size = 20;
  sort = 'createdAt,desc';

  readonly statusOptions: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'BLOCKED', 'DONE', 'CANCELLED'];
  readonly priorityOptions: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT', 'CRITICAL'];
  readonly typeOptions: TaskType[] = ['TASK', 'FEATURE', 'BUG', 'IMPROVEMENT', 'OTHER'];

  // ─── Modal: Create/Edit ────────────────────────────────────────────
  showModal = signal(false);
  isEditMode = signal(false);
  editingTaskId: string | null = null;
  editingVersion = 0;
  formError = signal<string | null>(null);
  formSubmitting = signal(false);
  taskForm!: FormGroup;

  // ─── Modal: Status Change ──────────────────────────────────────────
  showStatusModal = signal(false);
  targetTask: TaskSummaryResponse | null = null;
  newStatus: TaskStatus = 'IN_PROGRESS';
  blockerReason = '';
  statusModalError = signal<string | null>(null);
  statusSubmitting = signal(false);

  // ─── Modal: Delete ────────────────────────────────────────────────
  showDeleteModal = signal(false);
  deletingTask: TaskSummaryResponse | null = null;
  deleteSubmitting = signal(false);

  // ─── Keyword debounce ─────────────────────────────────────────────
  private keywordSubject = new Subject<string>();

  constructor() {
    this.taskForm = this.fb.group({
      projectId:   ['', [Validators.required]],
      title:       ['', [Validators.required, Validators.maxLength(200)]],
      type:        ['TASK'],
      priority:    ['MEDIUM'],
      assigneeId:  [''],
      startDate:   [''],
      dueDate:     [''],
      description: [''],
      notes:       [''],
      estimateMinutes: [null],
      progress:    [0, [Validators.min(0), Validators.max(100)]],
    });

    this.taskForm.get('projectId')?.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(projectId => {
      this.loadProjectMembers(projectId);
    });
  }

  loadProjectMembers(projectId: string): void {
    if (!projectId) {
      this.projectMembers.set([]);
      return;
    }
    this.projectService.getMembers(projectId).subscribe({
      next: members => this.projectMembers.set(members || []),
      error: () => this.projectMembers.set([])
    });
  }

  ngOnInit(): void {
    this.loadFilterOptions();
    this.loadTasks();
    this.keywordSubject.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.page = 0;
      this.loadTasks();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─── Load Data ────────────────────────────────────────────────────

  loadFilterOptions(): void {
    this.projectService.getProjectsOptions().subscribe(prjs => this.projectsList.set(prjs));
    this.projectService.getUsersList().subscribe(users => this.usersList.set(users));
  }

  loadTasks(): void {
    this.loading.set(true);
    this.error.set(null);

    const qf = this.quickFilter();

    if (qf === 'my-tasks') {
      this.taskService.getMyTasks(this.page, this.size).subscribe(this.handleResponse());
      return;
    }
    if (qf === 'today') {
      this.taskService.getTodayTasks(this.page, this.size).subscribe(this.handleResponse());
      return;
    }
    if (qf === 'overdue') {
      this.taskService.getOverdueTasks(this.page, this.size).subscribe(this.handleResponse());
      return;
    }

    const params: TaskFilterParams = {
      keyword:     this.keyword || undefined,
      projectId:   this.selectedProjectId || undefined,
      assigneeId:  this.selectedAssigneeId || undefined,
      status:      this.selectedStatuses.length ? this.selectedStatuses : undefined,
      priority:    this.selectedPriorities.length ? this.selectedPriorities : undefined,
      type:        this.selectedTypes.length ? this.selectedTypes : undefined,
      overdue:     this.filterOverdue || undefined,
      blocked:     qf === 'blocked' ? true : (this.filterBlocked || undefined),
      dueDateFrom: this.dueDateFrom || undefined,
      dueDateTo:   this.dueDateTo || undefined,
      page:        this.page,
      size:        this.size,
      sort:        this.sort,
    };

    this.taskService.getTasks(params).subscribe(this.handleResponse());
  }

  private handleResponse() {
    return {
      next: (res: any) => {
        const mappedContent = (res.content || []).map((task: any) => {
          if (task.assignee) {
            return {
              ...task,
              assigneeId: task.assignee?.id || task.assigneeId,
              assigneeName: task.assignee?.fullName?.length > 200 ? task.assignee.fullName.substring(0, 200) : task.assignee?.fullName || task.assigneeName
            };
          }
          return task;
        });
        this.tasks.set(mappedContent);
        this.totalElements.set(res.totalElements || 0);
        this.totalPages.set(res.totalPages || 0);
        this.loading.set(false);
      },
      error: (err: any) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || err?.message || 'Không thể tải danh sách công việc');
      }
    };
  }

  // ─── Filters ─────────────────────────────────────────────────────

  setQuickFilter(filter: QuickFilter): void {
    this.quickFilter.set(filter);
    this.page = 0;
    this.loadTasks();
  }

  onKeywordChange(): void {
    this.keywordSubject.next(this.keyword);
  }

  onFilterChange(): void {
    this.quickFilter.set('all');
    this.page = 0;
    this.loadTasks();
  }

  toggleStatus(status: string): void {
    const idx = this.selectedStatuses.indexOf(status);
    if (idx >= 0) this.selectedStatuses.splice(idx, 1);
    else this.selectedStatuses.push(status);
    this.onFilterChange();
  }

  togglePriority(priority: string): void {
    const idx = this.selectedPriorities.indexOf(priority);
    if (idx >= 0) this.selectedPriorities.splice(idx, 1);
    else this.selectedPriorities.push(priority);
    this.onFilterChange();
  }

  resetFilter(): void {
    this.keyword = '';
    this.selectedProjectId = '';
    this.selectedAssigneeId = '';
    this.selectedStatuses = [];
    this.selectedPriorities = [];
    this.selectedTypes = [];
    this.filterOverdue = false;
    this.filterBlocked = false;
    this.dueDateFrom = '';
    this.dueDateTo = '';
    this.quickFilter.set('all');
    this.page = 0;
    this.loadTasks();
  }

  hasActiveFilters(): boolean {
    return !!(this.keyword || this.selectedProjectId || this.selectedAssigneeId ||
      this.selectedStatuses.length || this.selectedPriorities.length ||
      this.filterOverdue || this.filterBlocked || this.dueDateFrom || this.dueDateTo);
  }

  // ─── Pagination ───────────────────────────────────────────────────

  get pages(): number[] {
    const total = this.totalPages();
    return Array.from({ length: total }, (_, i) => i);
  }

  goToPage(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.page = p;
    this.loadTasks();
  }

  // ─── Kanban Helper ───────────────────────────────────────────────

  getKanbanTasks(status: TaskStatus): TaskSummaryResponse[] {
    return this.tasks().filter(t => t.status === status);
  }

  getNextStatus(status: TaskStatus): TaskStatus | null {
    const map: Partial<Record<TaskStatus, TaskStatus>> = {
      'TODO': 'IN_PROGRESS',
      'IN_PROGRESS': 'REVIEW',
      'REVIEW': 'DONE',
    };
    return map[status] ?? null;
  }

  getNextStatusLabel(status: TaskStatus): string {
    const next = this.getNextStatus(status);
    if (!next) return '';
    const labels: Record<string, string> = {
      'IN_PROGRESS': 'Bắt đầu',
      'REVIEW': 'Review',
      'DONE': 'Hoàn thành'
    };
    return labels[next] || next;
  }

  // ─── Helpers ─────────────────────────────────────────────────────

  isOverdue(task: TaskSummaryResponse): boolean {
    if (!task.dueDate || task.status === 'DONE' || task.status === 'CANCELLED') return false;
    return task.dueDate < new Date().toISOString().slice(0, 10);
  }

  getProgressColor(progress: number): string {
    if (progress >= 100) return '#22c55e';
    if (progress >= 60)  return '#3b82f6';
    if (progress >= 30)  return '#f59e0b';
    return '#ef4444';
  }

  getPriorityIcon(priority: string): string {
    const icons: Record<string, string> = {
      'LOW': '🟢', 'MEDIUM': '🟡', 'HIGH': '🟠', 'URGENT': '🔴', 'CRITICAL': '🚨'
    };
    return icons[priority] || '';
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = {
      'TODO': 'Cần làm', 'IN_PROGRESS': 'Đang làm', 'REVIEW': 'Review',
      'DONE': 'Hoàn thành', 'BLOCKED': 'Tắc nghẽn', 'CANCELLED': 'Đã hủy'
    };
    return map[status] || status;
  }

  getRoleLabel(role: string): string {
    const map: Record<string, string> = {
      'PROJECT_MANAGER': 'PM',
      'TECH_LEAD': 'Tech Lead',
      'DEVELOPER': 'Developer',
      'DEV': 'Developer',
      'TESTER': 'Tester',
      'BUSINESS_ANALYST': 'BA',
      'BA': 'BA',
      'DEVOPS': 'DevOps',
      'MEMBER': 'Thành viên',
      'PROJECT_MEMBER': 'Thành viên'
    };
    return map[role] || role;
  }

  private extractError(err: any, fallback: string): string {
    if (!err) return fallback;
    if (err.fieldErrors && Array.isArray(err.fieldErrors) && err.fieldErrors.length > 0) {
      return err.fieldErrors.map((f: any) => f.message).join('. ');
    }
    if (err.message) return err.message;
    if (err.error?.fieldErrors && Array.isArray(err.error.fieldErrors) && err.error.fieldErrors.length > 0) {
      return err.error.fieldErrors.map((f: any) => f.message).join('. ');
    }
    if (err.error?.message) return err.error.message;
    return fallback;
  }

  // ─── Create / Edit Modal ─────────────────────────────────────────

  openCreateModal(): void {
    this.isEditMode.set(false);
    this.editingTaskId = null;
    this.formError.set(null);
    const initialProjectId = this.selectedProjectId || (this.projectsList().length > 0 ? this.projectsList()[0].id : '');
    this.loadProjectMembers(initialProjectId);
    this.taskForm.reset({
      projectId:   initialProjectId,
      title: '', type: 'TASK', priority: 'MEDIUM',
      assigneeId: '', startDate: '', dueDate: '',
      description: '', notes: '', estimateMinutes: null, progress: 0,
    });
    this.showModal.set(true);
  }

  openEditModal(task: TaskSummaryResponse): void {
    this.isEditMode.set(true);
    this.editingTaskId = task.id;
    this.formError.set(null);
    this.taskService.getTask(task.id).subscribe({
      next: detail => {
        this.editingVersion = detail.version;
        this.loadProjectMembers(detail.projectId);
        this.taskForm.patchValue({
          projectId:   detail.projectId,
          title:       detail.title,
          type:        detail.type,
          priority:    detail.priority,
          assigneeId:  detail.assignee?.id || '',
          startDate:   detail.startDate || '',
          dueDate:     detail.dueDate || '',
          description: detail.description || '',
          notes:       detail.notes || '',
          estimateMinutes: detail.estimateMinutes ?? null,
          progress:    detail.progress,
        });
        this.showModal.set(true);
      },
      error: err => {
        this.error.set(this.extractError(err, 'Không thể tải chi tiết công việc'));
      }
    });
  }

  closeModal(): void { this.showModal.set(false); }

  saveTask(): void {
    if (this.taskForm.invalid || this.formSubmitting()) {
      this.taskForm.markAllAsTouched();
      return;
    }
    this.formSubmitting.set(true);
    this.formError.set(null);
    const v = this.taskForm.value;

    if (this.isEditMode() && this.editingTaskId) {
      const req = {
        title: v.title, type: v.type, priority: v.priority,
        assigneeId: v.assigneeId || null, startDate: v.startDate || null,
        dueDate: v.dueDate || null, description: v.description || null,
        notes: v.notes || null, estimateMinutes: v.estimateMinutes || null,
        progress: v.progress ?? 0, version: this.editingVersion,
      };
      this.taskService.updateTask(this.editingTaskId, req as any).subscribe({
        next: () => { this.formSubmitting.set(false); this.closeModal(); this.loadTasks(); },
        error: (err) => { this.formSubmitting.set(false); this.formError.set(this.extractError(err, 'Cập nhật thất bại')); }
      });
    } else {
      this.taskService.createTask({
        projectId: v.projectId, title: v.title, type: v.type, priority: v.priority,
        assigneeId: v.assigneeId || undefined, startDate: v.startDate || undefined,
        dueDate: v.dueDate || undefined, description: v.description || undefined,
        notes: v.notes || undefined, estimateMinutes: v.estimateMinutes || undefined,
        progress: v.progress ?? 0,
      }).subscribe({
        next: () => { this.formSubmitting.set(false); this.closeModal(); this.loadTasks(); },
        error: (err) => { this.formSubmitting.set(false); this.formError.set(this.extractError(err, 'Tạo mới thất bại')); }
      });
    }
  }

  // ─── Status Change Modal ─────────────────────────────────────────

  allowedStatuses = signal<{ value: TaskStatus; label: string }[]>([]);

  openStatusModal(task: TaskSummaryResponse, defaultStatus?: TaskStatus): void {
    this.targetTask = task;
    this.blockerReason = task.blockerReason || '';
    this.statusModalError.set(null);

    const statuses = this.getAllowedStatuses(task.status);
    this.allowedStatuses.set(statuses);
    this.newStatus = defaultStatus && statuses.some(s => s.value === defaultStatus)
      ? defaultStatus
      : (statuses[0]?.value ?? 'IN_PROGRESS');
    this.showStatusModal.set(true);
  }

  getAllowedStatuses(current: TaskStatus): { value: TaskStatus; label: string }[] {
    switch (current) {
      case 'TODO':
        return [
          { value: 'IN_PROGRESS', label: '🔄 Đang làm (IN_PROGRESS)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      case 'IN_PROGRESS':
        return [
          { value: 'REVIEW', label: '🔍 Đang review (REVIEW)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      case 'REVIEW':
        return [
          { value: 'DONE', label: '✅ Hoàn thành (DONE)' },
          { value: 'IN_PROGRESS', label: '🔄 Cần sửa - Về Đang làm (IN_PROGRESS)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      case 'BLOCKED':
        return [
          { value: 'IN_PROGRESS', label: '🔄 Tiếp tục làm (IN_PROGRESS)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      default:
        return [
          { value: 'TODO', label: '📌 Cần làm (TODO)' },
          { value: 'IN_PROGRESS', label: '🔄 Đang làm (IN_PROGRESS)' },
          { value: 'REVIEW', label: '🔍 Đang review (REVIEW)' },
          { value: 'DONE', label: '✅ Hoàn thành (DONE)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
    }
  }

  closeStatusModal(): void {
    this.showStatusModal.set(false);
    this.targetTask = null;
  }

  confirmStatusChange(): void {
    if (!this.targetTask || this.statusSubmitting()) return;
    if (this.newStatus === 'BLOCKED' && !this.blockerReason.trim()) {
      this.statusModalError.set('Vui lòng nhập lý do bị tắc nghẽn (Blocker Reason)');
      return;
    }
    this.statusSubmitting.set(true);
    this.taskService.updateStatus(this.targetTask.id, {
      status: this.newStatus,
      blockerReason: this.newStatus === 'BLOCKED' ? this.blockerReason : undefined
    }).subscribe({
      next: () => {
        this.statusSubmitting.set(false);
        this.closeStatusModal();
        this.loadTasks();
      },
      error: (err) => {
        this.statusSubmitting.set(false);
        this.statusModalError.set(this.extractError(err, 'Chuyển trạng thái thất bại'));
      }
    });
  }

  // ─── Delete Modal ────────────────────────────────────────────────

  openDeleteModal(task: TaskSummaryResponse): void {
    this.deletingTask = task;
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deletingTask = null;
  }

  confirmDelete(): void {
    if (!this.deletingTask || this.deleteSubmitting()) return;
    this.deleteSubmitting.set(true);
    this.taskService.deleteTask(this.deletingTask.id).subscribe({
      next: () => {
        this.deleteSubmitting.set(false);
        this.closeDeleteModal();
        this.loadTasks();
      },
      error: (err) => {
        this.deleteSubmitting.set(false);
        alert(this.extractError(err, 'Xóa công việc thất bại'));
      }
    });
  }

  // ─── Export ──────────────────────────────────────────────────────

  exportExcel(): void {
    this.taskService.exportExcel({
      keyword: this.keyword || undefined,
      projectId: this.selectedProjectId || undefined,
      status: this.selectedStatuses.length ? this.selectedStatuses : undefined,
      priority: this.selectedPriorities.length ? this.selectedPriorities : undefined,
    }).subscribe(blob => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `PMDaily_Tasks_${new Date().toISOString().slice(0, 10)}.xlsx`;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }
}
