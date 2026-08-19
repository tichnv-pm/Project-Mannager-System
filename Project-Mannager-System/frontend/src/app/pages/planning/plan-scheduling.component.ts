import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PlanService } from './plan.service';
import {
  CriticalPathResult,
  CriticalTaskDto,
  RecalcResponse,
  SCHEDULING_WARNING_LABELS,
  SchedulingWarningDto,
  PLAN_TASK_TYPE_LABELS
} from './plan.model';

@Component({
  selector: 'app-plan-scheduling',
  standalone: true,
  imports: [CommonModule, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './plan-scheduling.component.html',
  styleUrls: ['./plan-scheduling.component.scss']
})
export class PlanSchedulingComponent implements OnInit {
  @Input() planId!: string;
  @Output() changed = new EventEmitter<void>();

  private planService = inject(PlanService);

  loading = signal(true);
  error = signal<string | null>(null);

  recalc = signal<RecalcResponse | null>(null);
  recalcBusy = signal(false);
  recalcError = signal<string | null>(null);

  critical = signal<CriticalPathResult | null>(null);
  criticalLoading = signal(true);
  criticalError = signal<string | null>(null);

  readonly warningLabels = SCHEDULING_WARNING_LABELS;
  readonly typeLabels = PLAN_TASK_TYPE_LABELS;

  ngOnInit(): void {
    this.loadCritical();
    this.loading.set(false);
  }

  loadCritical(): void {
    this.criticalLoading.set(true);
    this.criticalError.set(null);
    this.planService.getCriticalPath(this.planId).subscribe({
      next: (res) => {
        this.critical.set(res);
        this.criticalLoading.set(false);
      },
      error: (err) => {
        this.criticalLoading.set(false);
        this.criticalError.set(err.message || 'Không thể tải critical path');
      }
    });
  }

  runRecalc(): void {
    if (this.recalcBusy()) return;
    this.recalcBusy.set(true);
    this.recalcError.set(null);
    this.planService.recalculatePlan(this.planId).subscribe({
      next: (res) => {
        this.recalc.set(res);
        this.recalcBusy.set(false);
        this.loadCritical();
        this.changed.emit();
      },
      error: (err) => {
        this.recalcBusy.set(false);
        this.recalcError.set(err.message || 'Recalculation thất bại');
      }
    });
  }

  warningTypeClass(type: string): string {
    switch (type) {
      case 'CONSTRAINT_CONFLICT':
        return 'warn-constraint';
      case 'DATE_NOT_WORKING':
        return 'warn-date';
      case 'NEGATIVE_LAG':
        return 'warn-lag';
      case 'CYCLE_DEPENDENCY':
        return 'warn-cycle';
      default:
        return 'warn-anchor';
    }
  }

  formatMinutes(min?: number): string {
    if (min == null) return '—';
    if (min < 60) return `${min} phút`;
    const hours = min / 60;
    if (hours < 24) return `${Math.round(hours)} giờ`;
    return `${Math.round(hours / 8)} ngày`;
  }

  formatFloat(min: number): string {
    if (min === 0) return '0';
    return this.formatMinutes(min);
  }

  criticalTasks(): CriticalTaskDto[] {
    return this.critical()?.tasks ?? [];
  }

  criticalCount(): number {
    return this.critical()?.criticalTaskCount ?? 0;
  }

  warningList(): SchedulingWarningDto[] {
    return this.recalc()?.warnings ?? [];
  }
}