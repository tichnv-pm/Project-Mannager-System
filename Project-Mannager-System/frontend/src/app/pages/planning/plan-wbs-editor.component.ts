import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserBrief } from '../../core/models/auth.model';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectService } from '../projects/project.service';
import { PlanService } from './plan.service';
import {
  LEAF_TASK_TYPES,
  PLAN_TASK_STATUS_LABELS,
  PLAN_TASK_TYPE_LABELS,
  PlanTaskCreateRequest,
  PlanTaskResponse,
  PlanTaskType,
  PlanTaskUpdateRequest,
  SUMMARY_TASK_TYPES,
  TASK_PRIORITY_LABELS
} from './plan.model';

const UNIT_FACTORS: Record<string, number> = {
  MINUTE: 1,
  HOUR: 60,
  DAY: 480,
  WEEK: 2400,
  MONTH: 9600
};

const UNIT_LABELS: Record<string, string> = {
  MINUTE: 'phút',
  HOUR: 'giờ',
  DAY: 'ngày',
  WEEK: 'tuần',
  MONTH: 'tháng'
};

interface WbsRow {
  task: PlanTaskResponse;
  depth: number;
  expanded: boolean;
  hasChildren: boolean;
}

@Component({
  selector: 'app-plan-wbs-editor',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    StatusChipComponent,
    EmptyStateComponent,
    HasPermissionDirective
  ],
  templateUrl: './plan-wbs-editor.component.html',
  styleUrls: ['./plan-wbs-editor.component.scss']
})
export class PlanWbsEditorComponent implements OnInit {
  @Input() planId!: string;
  @Input() projectId?: string;
  @Output() changed = new EventEmitter<void>();

  private planService = inject(PlanService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  loading = signal(true);
  error = signal<string | null>(null);
  rows = signal<WbsRow[]>([]);
  tasksById = new Map<string, PlanTaskResponse>();
  childrenByParent = new Map<string | undefined, PlanTaskResponse[]>();
  usersList = signal<UserBrief[]>([]);

  // Modal State
  showModal = signal(false);
  isEditMode = signal(false);
  modalTitle = '';
  editingTask: PlanTaskResponse | null = null;
  formParentId: string | undefined = undefined;
  formError = signal<string | null>(null);
  formSubmitting = signal(false);

  busyTaskIds = new Set<string>();

  taskForm: FormGroup;

  readonly summaryTypes = SUMMARY_TASK_TYPES;
  readonly leafTypes = LEAF_TASK_TYPES;
  readonly typeLabels = PLAN_TASK_TYPE_LABELS;
  readonly statusOptions = Object.keys(PLAN_TASK_STATUS_LABELS);
  readonly priorityOptions = Object.keys(TASK_PRIORITY_LABELS);

  constructor() {
    this.taskForm = this.fb.group({
      taskType: ['TASK', [Validators.required]],
      taskCode: ['', [Validators.required, Validators.maxLength(40)]],
      taskName: ['', [Validators.required, Validators.maxLength(200)]],
      description: [''],
      ownerId: [''],
      plannedStart: [''],
      plannedFinish: [''],
      durationVal: [''],
      durationUnit: ['MINUTE'],
      effortVal: [''],
      effortUnit: ['MINUTE'],
      percentComplete: [0, [Validators.min(0), Validators.max(100)]],
      status: ['NOT_STARTED'],
      priority: ['MEDIUM'],
      scheduleMode: ['AUTO']
    });
  }

  ngOnInit(): void {
    this.loadTree();
    this.projectService.getUsersList().subscribe(users => this.usersList.set(users));
  }

  loadTree(): void {
    this.loading.set(true);
    this.error.set(null);

    this.planService.getTasks(this.planId).subscribe({
      next: (tasks) => {
        this.tasksById = new Map(tasks.map(t => [t.id, t]));
        this.buildChildrenMap(tasks);
        this.rows.set(this.buildVisibleRows(tasks));
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải WBS');
      }
    });
  }

  private buildChildrenMap(tasks: PlanTaskResponse[]): void {
    this.childrenByParent = new Map<string | undefined, PlanTaskResponse[]>();
    for (const t of tasks) {
      const key = t.parentId ?? undefined;
      if (!this.childrenByParent.has(key)) this.childrenByParent.set(key, []);
      this.childrenByParent.get(key)!.push(t);
    }
  }

  private buildVisibleRows(tasks: PlanTaskResponse[], parentId?: string, depth = 0): WbsRow[] {
    const kids = this.childrenByParent.get(parentId) || [];
    const result: WbsRow[] = [];
    for (const task of kids) {
      const childCount = (this.childrenByParent.get(task.id) || []).length;
      const expanded = this.expandedIds.has(task.id);
      const row: WbsRow = { task, depth, expanded, hasChildren: childCount > 0 };
      result.push(row);
      if (expanded && childCount > 0) {
        result.push(...this.buildVisibleRows(tasks, task.id, depth + 1));
      }
    }
    return result;
  }

  private expandedIds = new Set<string>();

  toggleExpand(task: PlanTaskResponse): void {
    if (this.expandedIds.has(task.id)) {
      this.expandedIds.delete(task.id);
    } else {
      this.expandedIds.add(task.id);
    }
    this.rows.set(this.buildVisibleRows([...this.tasksById.values()]));
  }

  isLeaf(type: PlanTaskType): boolean {
    return LEAF_TASK_TYPES.includes(type);
  }

  statusLabel(status: string): string {
    return PLAN_TASK_STATUS_LABELS[status as keyof typeof PLAN_TASK_STATUS_LABELS] || status;
  }

  priorityLabel(priority: string): string {
    return TASK_PRIORITY_LABELS[priority as keyof typeof TASK_PRIORITY_LABELS] || priority;
  }

  ownerName(ownerId: string | undefined): string {
    if (!ownerId) return '—';
    const u = this.usersList().find(u => u.id === ownerId);
    return u ? (u.fullName || u.username) : ownerId;
  }

  // ─── Sibling helpers ─────────────────────────────────────────
  siblings(task: PlanTaskResponse): PlanTaskResponse[] {
    return this.childrenByParent.get(task.parentId ?? undefined) || [];
  }

  canMoveUp(task: PlanTaskResponse): boolean {
    const sibs = this.siblings(task);
    return sibs.length > 0 && sibs[0].id !== task.id;
  }

  canMoveDown(task: PlanTaskResponse): boolean {
    const sibs = this.siblings(task);
    return sibs.length > 0 && sibs[sibs.length - 1].id !== task.id;
  }

  canIndent(task: PlanTaskResponse): boolean {
    const sibs = this.siblings(task);
    return sibs.length > 0 && sibs[0].id !== task.id;
  }

  canOutdent(task: PlanTaskResponse): boolean {
    return !!task.parentId;
  }

  move(task: PlanTaskResponse, direction: 'UP' | 'DOWN' | 'INDENT' | 'OUTDENT'): void {
    this.busyTaskIds.add(task.id);
    this.planService.moveTask(this.planId, task.id, { direction }).subscribe({
      next: () => {
        this.busyTaskIds.delete(task.id);
        this.loadTree();
        this.changed.emit();
      },
      error: (err) => {
        this.busyTaskIds.delete(task.id);
        alert(err.message || 'Di chuyển task thất bại');
      }
    });
  }

  // ─── Create / Edit ───────────────────────────────────────────
  openAddRoot(): void {
    this.openModal(false, undefined, 'Thêm task gốc');
  }

  openAddChild(parent: PlanTaskResponse): void {
    this.openModal(false, parent.id, `Thêm task con cho ${parent.taskName}`);
  }

  openAddSibling(task: PlanTaskResponse): void {
    this.openModal(false, task.parentId, 'Thêm task cùng cấp');
  }

  openEdit(task: PlanTaskResponse): void {
    this.editingTask = task;
    this.formParentId = task.parentId;
    this.isEditMode.set(true);
    this.modalTitle = `Sửa ${task.wbsCode} — ${task.taskName}`;
    this.formError.set(null);

    const dUnit = task.durationUnit || 'MINUTE';
    const dFactor = UNIT_FACTORS[dUnit] || 1;
    const dVal = task.durationMinutes != null ? task.durationMinutes / dFactor : '';

    const eUnit = task.effortUnit || 'MINUTE';
    const eFactor = UNIT_FACTORS[eUnit] || 1;
    const eVal = task.plannedEffortMinutes != null ? task.plannedEffortMinutes / eFactor : '';

    this.taskForm.patchValue({
      taskType: task.taskType,
      taskCode: task.taskCode,
      taskName: task.taskName,
      description: task.description || '',
      ownerId: task.ownerId || '',
      plannedStart: task.plannedStart || '',
      plannedFinish: task.plannedFinish || '',
      durationVal: dVal,
      durationUnit: dUnit,
      effortVal: eVal,
      effortUnit: eUnit,
      percentComplete: task.percentComplete,
      status: task.status,
      priority: task.priority || 'MEDIUM',
      scheduleMode: task.scheduleMode
    });
    const codeControl = this.taskForm.get('taskCode');
    if (codeControl) {
      this.isEditMode() ? codeControl.disable() : codeControl.enable();
    }
    this.showModal.set(true);
  }

  private openModal(isEdit: boolean, parentId: string | undefined, title: string): void {
    this.isEditMode.set(isEdit);
    this.editingTask = null;
    this.formParentId = parentId;
    this.modalTitle = title;
    this.formError.set(null);
    this.taskForm.reset({
      taskType: 'TASK',
      taskCode: '',
      taskName: '',
      description: '',
      ownerId: '',
      plannedStart: '',
      plannedFinish: '',
      durationVal: '',
      durationUnit: 'MINUTE',
      effortVal: '',
      effortUnit: 'MINUTE',
      percentComplete: 0,
      status: 'NOT_STARTED',
      priority: 'MEDIUM',
      scheduleMode: 'AUTO'
    });
    const codeControl = this.taskForm.get('taskCode');
    if (codeControl) codeControl.enable();
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
    this.editingTask = null;
  }

  onTaskTypeChange(): void {
    const type = this.taskForm.get('taskType')?.value as PlanTaskType;
    if (type === 'MILESTONE') {
      this.taskForm.patchValue({ durationVal: '', effortVal: '' });
    }
  }

  saveTask(): void {
    if (this.taskForm.invalid || this.formSubmitting()) {
      this.taskForm.markAllAsTouched();
      return;
    }

    const v = this.taskForm.getRawValue();
    if (v.plannedStart && v.plannedFinish && v.plannedFinish < v.plannedStart) {
      this.formError.set('Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu');
      return;
    }

    let durationMinutes: number | undefined = undefined;
    if (v.taskType !== 'MILESTONE' && v.durationVal !== '' && v.durationVal != null) {
      const dUnit = v.durationUnit || 'MINUTE';
      const dFactor = UNIT_FACTORS[dUnit] || 1;
      durationMinutes = Math.round(Number(v.durationVal) * dFactor);
    } else if (v.taskType === 'MILESTONE') {
      durationMinutes = 0;
    }

    let plannedEffortMinutes: number | undefined = undefined;
    if (v.taskType !== 'MILESTONE' && v.effortVal !== '' && v.effortVal != null) {
      const eUnit = v.effortUnit || 'MINUTE';
      const eFactor = UNIT_FACTORS[eUnit] || 1;
      plannedEffortMinutes = Math.round(Number(v.effortVal) * eFactor);
    } else if (v.taskType === 'MILESTONE') {
      plannedEffortMinutes = 0;
    }

    const base = {
      taskName: v.taskName,
      description: v.description || undefined,
      ownerId: v.ownerId || undefined,
      taskType: v.taskType as PlanTaskType,
      plannedStart: v.plannedStart || undefined,
      plannedFinish: v.plannedFinish || undefined,
      durationMinutes,
      durationUnit: v.taskType === 'MILESTONE' ? 'MINUTE' : (v.durationUnit || 'MINUTE'),
      plannedEffortMinutes,
      effortUnit: v.taskType === 'MILESTONE' ? 'MINUTE' : (v.effortUnit || 'MINUTE'),
      percentComplete: Number(v.percentComplete),
      status: v.status,
      priority: v.priority,
      scheduleMode: v.scheduleMode
    };

    this.formSubmitting.set(true);
    this.formError.set(null);

    if (this.isEditMode() && this.editingTask) {
      const req: PlanTaskUpdateRequest = { ...base, version: this.editingTask.version };
      this.planService.updateTask(this.planId, this.editingTask.id, req).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadTree();
          this.changed.emit();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Cập nhật task thất bại');
        }
      });
    } else {
      const req: PlanTaskCreateRequest = {
        ...base,
        taskCode: v.taskCode,
        parentId: this.formParentId
      };
      this.planService.createTask(this.planId, req).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadTree();
          this.changed.emit();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Tạo task thất bại');
        }
      });
    }
  }

  // ─── Delete ──────────────────────────────────────────────────
  deleteTask(task: PlanTaskResponse): void {
    if (!confirm(`Xóa ${task.wbsCode} — ${task.taskName}?`)) return;
    this.busyTaskIds.add(task.id);
    this.planService.deleteTask(this.planId, task.id).subscribe({
      next: () => {
        this.busyTaskIds.delete(task.id);
        this.loadTree();
        this.changed.emit();
      },
      error: (err) => {
        this.busyTaskIds.delete(task.id);
        alert(err.message || 'Xóa task thất bại');
      }
    });
  }

  createChildPlan(task: PlanTaskResponse): void {
    this.router.navigate(['/plans'], {
      queryParams: {
        projectId: this.projectId || '',
        parentPlanId: this.planId,
        parentMilestoneTaskId: task.id,
        action: 'create'
      }
    });
  }

  formatValueWithUnit(minutes: number | undefined, unit: string | undefined): string {
    if (minutes == null) return '—';
    const u = unit || 'MINUTE';
    const factor = UNIT_FACTORS[u] || 1;
    const displayVal = minutes / factor;
    const rounded = Math.round(displayVal * 100) / 100;
    return `${rounded} ${UNIT_LABELS[u]}`;
  }
}