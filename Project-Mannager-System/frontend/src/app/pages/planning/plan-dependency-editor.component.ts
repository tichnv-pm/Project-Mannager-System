import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PlanService } from './plan.service';
import {
  DEPENDENCY_TYPE_LABELS,
  DEPENDENCY_TYPE_SHORT,
  DependencyResponse,
  DependencyType,
  PlanTaskResponse,
  PLAN_TASK_TYPE_LABELS
} from './plan.model';

interface DepRow {
  dep: DependencyResponse;
  predLabel: string;
  succLabel: string;
}

@Component({
  selector: 'app-plan-dependency-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './plan-dependency-editor.component.html',
  styleUrls: ['./plan-dependency-editor.component.scss']
})
export class PlanDependencyEditorComponent implements OnInit {
  @Input() planId!: string;
  @Output() changed = new EventEmitter<void>();

  private planService = inject(PlanService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);
  tasks = signal<PlanTaskResponse[]>([]);
  deps = signal<DependencyResponse[]>([]);
  rows = signal<DepRow[]>([]);

  busy = signal(false);
  formError = signal<string | null>(null);

  readonly typeLabels = DEPENDENCY_TYPE_LABELS;
  readonly typeKeys = Object.keys(DEPENDENCY_TYPE_LABELS) as DependencyType[];
  readonly taskTypeLabels = PLAN_TASK_TYPE_LABELS;
  readonly shortType = DEPENDENCY_TYPE_SHORT;

  form: FormGroup;

  constructor() {
    this.form = this.fb.group({
      predecessorTaskId: ['', Validators.required],
      successorTaskId: ['', Validators.required],
      dependencyType: ['FS', Validators.required],
      lagMinutes: [0]
    });
  }

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planService.getTasks(this.planId).subscribe({
      next: (tasks) => {
        this.tasks.set(tasks);
        this.planService.getDependencies(this.planId).subscribe({
          next: (deps) => this.applyDeps(deps),
          error: (err) => {
            this.loading.set(false);
            this.error.set(err.message || 'Không thể tải danh sách dependency');
          }
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải danh sách task');
      }
    });
  }

  private applyDeps(deps: DependencyResponse[]): void {
    this.deps.set(deps);
    const taskById = new Map(this.tasks().map((t) => [t.id, t]));
    this.rows.set(
      deps.map((dep) => ({
        dep,
        predLabel: this.taskLabel(taskById.get(dep.predecessorTaskId)),
        succLabel: this.taskLabel(taskById.get(dep.successorTaskId))
      }))
    );
    this.loading.set(false);
  }

  private taskLabel(task: PlanTaskResponse | undefined): string {
    if (!task) return '—';
    return `${task.taskCode} — ${task.taskName}`;
  }

  availablePredecessors(successorId: string): PlanTaskResponse[] {
    if (!successorId) return this.tasks();
    return this.tasks().filter((t) => t.id !== successorId);
  }

  onSuccessorChange(): void {
    const succ = this.form.get('successorTaskId')?.value;
    const pred = this.form.get('predecessorTaskId')?.value;
    if (succ && pred && succ === pred) {
      this.form.get('predecessorTaskId')?.setValue('');
    }
  }

  lagMinutesFor(dep: DependencyResponse): string {
    if (dep.lagMinutes === 0) return '0';
    return `${dep.lagMinutes > 0 ? '+' : ''}${dep.lagMinutes} phút`;
  }

  addDependency(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const val = this.form.value;
    if (val.predecessorTaskId === val.successorTaskId) {
      this.formError.set('Không thể tạo dependency của chính nó (predecessor ≠ successor)');
      return;
    }
    if (val.lagMinutes < 0) {
      this.formError.set('Lag âm (lead time) được phép nhưng cần kiểm tra kỹ lịch trình');
    } else {
      this.formError.set(null);
    }

    this.busy.set(true);
    this.planService
      .createDependency(this.planId, val.successorTaskId, {
        predecessorTaskId: val.predecessorTaskId,
        dependencyType: val.dependencyType,
        lagMinutes: val.lagMinutes || undefined
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.formError.set(null);
          this.form.get('predecessorTaskId')?.setValue('');
          this.reloadDeps();
        },
        error: (err) => {
          this.busy.set(false);
          this.formError.set(err.message || 'Tạo dependency thất bại');
        }
      });
  }

  deleteDependency(row: DepRow): void {
    if (this.busy()) return;
    if (!confirm(`Xóa dependency ${row.predLabel} → ${row.succLabel} [${DEPENDENCY_TYPE_SHORT[row.dep.dependencyType]}]?`)) {
      return;
    }
    this.busy.set(true);
    this.planService
      .deleteDependency(this.planId, row.dep.successorTaskId, row.dep.id)
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.reloadDeps();
        },
        error: (err) => {
          this.busy.set(false);
          this.formError.set(err.message || 'Xóa dependency thất bại');
        }
      });
  }

  private reloadDeps(): void {
    this.planService.getDependencies(this.planId).subscribe({
      next: (deps) => {
        this.applyDeps(deps);
        this.changed.emit();
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}