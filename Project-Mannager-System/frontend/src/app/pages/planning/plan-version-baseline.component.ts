import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PlanService } from './plan.service';
import {
  BaselineResponse,
  BaselineVarianceResponse,
  BaselineVarianceRow,
  TaskDiffResponse,
  VersionDiffResponse,
  VersionResponse,
  PLAN_TASK_TYPE_LABELS
} from './plan.model';

@Component({
  selector: 'app-plan-version-baseline',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './plan-version-baseline.component.html',
  styleUrls: ['./plan-version-baseline.component.scss']
})
export class PlanVersionBaselineComponent implements OnInit {
  @Input() planId!: string;
  @Input() planStatus = '';
  @Output() changed = new EventEmitter<void>();

  private planService = inject(PlanService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);

  versions = signal<VersionResponse[]>([]);
  baselines = signal<BaselineResponse[]>([]);

  busy = signal(false);
  actionError = signal<string | null>(null);

  showVersionModal = signal(false);
  showBaselineModal = signal(false);
  versionForm: FormGroup;
  baselineForm: FormGroup;

  showDiffModal = signal(false);
  diffBusy = signal(false);
  diffError = signal<string | null>(null);
  diff = signal<VersionDiffResponse | null>(null);

  showVarianceModal = signal(false);
  varianceBusy = signal(false);
  varianceError = signal<string | null>(null);
  variance = signal<BaselineVarianceResponse | null>(null);

  readonly typeLabels = PLAN_TASK_TYPE_LABELS;

  constructor() {
    this.versionForm = this.fb.group({
      note: ['', Validators.maxLength(500)]
    });
    this.baselineForm = this.fb.group({
      description: ['', Validators.maxLength(500)]
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planService.getVersions(this.planId).subscribe({
      next: (versions) => {
        this.versions.set(versions);
        this.planService.getBaselines(this.planId).subscribe({
          next: (baselines) => {
            this.baselines.set(baselines);
            this.loading.set(false);
          },
          error: (err) => {
            this.loading.set(false);
            this.error.set(err.message || 'Không thể tải danh sách baseline');
          }
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải danh sách version');
      }
    });
  }

  canBaseline(): boolean {
    return this.planStatus === 'APPROVED';
  }

  // ─── Version ────────────────────────────────────────────────
  openCreateVersion(): void {
    this.actionError.set(null);
    this.versionForm.reset({ note: '' });
    this.showVersionModal.set(true);
  }

  closeVersionModal(): void {
    this.showVersionModal.set(false);
  }

  createVersion(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.actionError.set(null);
    this.planService.createVersion(this.planId, this.versionForm.value.note || undefined).subscribe({
      next: () => {
        this.busy.set(false);
        this.showVersionModal.set(false);
        this.load();
        this.changed.emit();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Tạo version thất bại');
      }
    });
  }

  openDiff(v: VersionResponse): void {
    this.diff.set(null);
    this.showDiffModal.set(true);
    this.diffBusy.set(true);
    this.diffError.set(null);
    this.planService.getVersionDiff(this.planId, v.versionNo).subscribe({
      next: (res) => {
        this.diff.set(res);
        this.diffBusy.set(false);
      },
      error: (err) => {
        this.diffBusy.set(false);
        this.diffError.set(err.message || 'Không thể tải diff');
      }
    });
  }

  closeDiff(): void {
    this.showDiffModal.set(false);
  }

  diffRows(): TaskDiffResponse[] {
    return this.diff()?.tasks ?? [];
  }

  fmtValue(val?: unknown): string {
    if (val == null || val === '') return '—';
    return String(val);
  }

  // ─── Baseline ───────────────────────────────────────────────
  openCreateBaseline(): void {
    this.actionError.set(null);
    this.baselineForm.reset({ description: '' });
    this.showBaselineModal.set(true);
  }

  closeBaselineModal(): void {
    this.showBaselineModal.set(false);
  }

  createBaseline(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.actionError.set(null);
    this.planService.createBaseline(this.planId, this.baselineForm.value.description || undefined).subscribe({
      next: () => {
        this.busy.set(false);
        this.showBaselineModal.set(false);
        this.load();
        this.changed.emit();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Tạo baseline thất bại (chỉ plan APPROVED)');
      }
    });
  }

  openVariance(b: BaselineResponse): void {
    this.variance.set(null);
    this.showVarianceModal.set(true);
    this.varianceBusy.set(true);
    this.varianceError.set(null);
    this.planService.getBaselineVariance(this.planId, b.baselineNum).subscribe({
      next: (res) => {
        this.variance.set(res);
        this.varianceBusy.set(false);
      },
      error: (err) => {
        this.varianceBusy.set(false);
        this.varianceError.set(err.message || 'Không thể tải variance');
      }
    });
  }

  closeVariance(): void {
    this.showVarianceModal.set(false);
  }

  varianceRows(): BaselineVarianceRow[] {
    return this.variance()?.tasks ?? [];
  }

  deleteBaseline(b: BaselineResponse): void {
    if (!confirm(`Xóa baseline #${b.baselineNum}? Hành động soft delete.`)) return;
    this.busy.set(true);
    this.planService.deleteBaseline(this.planId, b.baselineNum).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Xóa baseline thất bại');
      }
    });
  }

  fmtMinutes(min?: number): string {
    if (min == null) return '—';
    if (min < 60) return `${min} phút`;
    const hours = min / 60;
    if (hours < 24) return `${Math.round(hours)} giờ`;
    return `${Math.round(hours / 8)} ngày`;
  }

  fmtDays(d?: number): string {
    if (d == null) return '—';
    return d === 0 ? '0' : `${d > 0 ? '+' : ''}${d} ngày`;
  }

  variantClass(v: number): string {
    if (v > 0) return 'var-bad';
    if (v < 0) return 'var-good';
    return 'var-zero';
  }
}