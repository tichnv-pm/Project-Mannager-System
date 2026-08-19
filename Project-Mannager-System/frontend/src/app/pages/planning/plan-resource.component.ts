import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectMemberResponse } from '../projects/project.model';
import { ProjectService } from '../projects/project.service';
import { PlanService } from './plan.service';
import {
  PlanTaskResponse,
  RESOURCE_TYPE_LABELS,
  ResourceAssignmentResponse,
  ResourceOverviewRow,
  ResourceType,
  WORKLOAD_GRANULARITY_LABELS,
  WorkloadGranularity,
  WorkloadResponse
} from './plan.model';

interface AllocationRow {
  allocation: ResourceAssignmentResponse;
  editMode: boolean;
  editError: string | null;
  editBusy: boolean;
}

@Component({
  selector: 'app-plan-resource',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './plan-resource.component.html',
  styleUrls: ['./plan-resource.component.scss']
})
export class PlanResourceComponent implements OnInit {
  @Input() planId!: string;
  @Output() changed = new EventEmitter<void>();

  private planService = inject(PlanService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);

  tasks = signal<PlanTaskResponse[]>([]);
  members = signal<ProjectMemberResponse[]>([]);
  projectId: string | null = null;

  allocations = signal<AllocationRow[]>([]);
  allocationsBusy = signal(false);

  assignBusy = signal(false);
  assignError = signal<string | null>(null);

  workload = signal<WorkloadResponse[]>([]);
  workloadLoading = signal(false);
  workloadError = signal<string | null>(null);

  overview = signal<ResourceOverviewRow[]>([]);
  overviewLoading = signal(false);
  overviewError = signal<string | null>(null);

  showCapacityModal = signal(false);
  capacityRow: ResourceOverviewRow | null = null;
  capacityBusy = signal(false);
  capacityError = signal<string | null>(null);

  defaultFrom = '2026-08-01';
  defaultTo = '2026-11-30';

  readonly typeLabels = RESOURCE_TYPE_LABELS;
  readonly typeKeys = Object.keys(RESOURCE_TYPE_LABELS) as ResourceType[];
  readonly granularityLabels = WORKLOAD_GRANULARITY_LABELS;
  readonly granularityKeys = Object.keys(WORKLOAD_GRANULARITY_LABELS) as WorkloadGranularity[];

  assignForm: FormGroup;
  capacityForm: FormGroup;

  workloadGranularity: WorkloadGranularity = 'DAY';
  workloadFrom: string;
  workloadTo: string;

  constructor() {
    const now = new Date();
    this.workloadTo = now.toISOString().slice(0, 10);
    const from = new Date(now);
    from.setMonth(now.getMonth() - 1);
    this.workloadFrom = from.toISOString().slice(0, 10);

    this.assignForm = this.fb.group({
      taskId: ['', Validators.required],
      resourceType: ['USER'],
      resourceId: ['', Validators.required],
      allocationPercent: [100, [Validators.min(1), Validators.max(100)]],
      roleOnTask: [''],
      startDate: [''],
      endDate: [''],
      plannedEffortMinutes: [null]
    });

    this.capacityForm = this.fb.group({
      capacityPercent: [100, [Validators.min(0), Validators.max(100)]],
      startDate: [this.defaultFrom, Validators.required],
      endDate: ['']
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planService.getPlan(this.planId).subscribe({
      next: (plan) => {
        this.projectId = plan.projectId;
        this.loadTasksAndMembers();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải thông tin kế hoạch');
      }
    });
  }

  private loadTasksAndMembers(): void {
    this.planService.getTasks(this.planId).subscribe({
      next: (tasks) => {
        this.tasks.set(tasks);
        this.loadMembers();
      },
      error: () => this.loadMembers()
    });
  }

  private loadMembers(): void {
    if (this.projectId) {
      this.projectService.getMembers(this.projectId).subscribe({
        next: (m) => {
          this.members.set(m);
          this.finishLoad();
        },
        error: () => this.finishLoad()
      });
    } else {
      this.finishLoad();
    }
  }

  private finishLoad(): void {
    this.loading.set(false);
    this.reloadAllocations();
  }

  reloadAllocations(): void {
    this.allocationsBusy.set(true);
    this.planService.getPlanResources(this.planId).subscribe({
      next: (list) => {
        this.allocations.set(
          list.map((a) => ({ allocation: a, editMode: false, editError: null, editBusy: false }))
        );
        this.allocationsBusy.set(false);
      },
      error: (err) => {
        this.allocationsBusy.set(false);
        this.error.set(err.message || 'Không thể tải danh sách resource');
      }
    });
  }

  onResourceTypeChange(): void {
    const type = this.assignForm.get('resourceType')?.value as ResourceType;
    const ctrl = this.assignForm.get('resourceId');
    if (type === 'USER') {
      ctrl?.setValue(this.members().length === 1 ? this.members()[0].userId : '');
    } else {
      ctrl?.setValue('');
    }
  }

  assign(): void {
    if (this.assignForm.invalid || this.assignBusy()) {
      this.assignForm.markAllAsTouched();
      return;
    }
    const v = this.assignForm.value;
    if (v.startDate && v.endDate && v.endDate < v.startDate) {
      this.assignError.set('Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu');
      return;
    }
    this.assignBusy.set(true);
    this.assignError.set(null);
    this.planService
      .assignResource(this.planId, v.taskId, {
        resourceType: v.resourceType,
        resourceId: v.resourceId,
        allocationPercent: v.allocationPercent,
        roleOnTask: v.roleOnTask || undefined,
        startDate: v.startDate || undefined,
        endDate: v.endDate || undefined,
        plannedEffortMinutes: v.plannedEffortMinutes ?? undefined
      })
      .subscribe({
        next: (res) => {
          this.assignBusy.set(false);
          if (res.overAllocation) {
            this.assignError.set(
              `⚠ Cảnh báo: resource "${res.resourceName}" đang bị over-allocation (${Math.round(res.utilizationPercent ?? 0)}%).`
            );
          } else {
            this.assignError.set(null);
            this.assignForm.patchValue({ resourceId: '', roleOnTask: '' });
          }
          this.reloadAllocations();
          this.changed.emit();
        },
        error: (err) => {
          this.assignBusy.set(false);
          this.assignError.set(err.message || 'Gán resource thất bại');
        }
      });
  }

  unassign(row: AllocationRow): void {
    if (!confirm(`Gỡ resource "${row.allocation.resourceName}" khỏi task ${row.allocation.taskCode}?`)) return;
    row.editBusy = true;
    this.planService.removeResourceAllocation(row.allocation.id).subscribe({
      next: () => {
        this.reloadAllocations();
        this.changed.emit();
      },
      error: (err) => {
        row.editBusy = false;
        row.editError = err.message || 'Gỡ resource thất bại';
      }
    });
  }

  editAllocation(row: AllocationRow): void {
    row.editMode = true;
    row.editError = null;
  }

  cancelEditAllocation(row: AllocationRow): void {
    row.editMode = false;
    row.editError = null;
  }

  saveAllocation(row: AllocationRow): void {
    if (row.editBusy) return;
    const input = document.getElementById(`alloc-pct-${row.allocation.id}`) as HTMLInputElement | null;
    const roleInput = document.getElementById(`alloc-role-${row.allocation.id}`) as HTMLInputElement | null;
    const startInput = document.getElementById(`alloc-start-${row.allocation.id}`) as HTMLInputElement | null;
    const endInput = document.getElementById(`alloc-end-${row.allocation.id}`) as HTMLInputElement | null;
    const percent = Number(input?.value ?? row.allocation.allocationPercent);
    if (percent < 1 || percent > 100) {
      row.editError = 'Allocation % phải từ 1-100';
      return;
    }
    row.editBusy = true;
    row.editError = null;
    this.planService
      .updateResourceAllocation(row.allocation.id, {
        allocationPercent: percent,
        roleOnTask: roleInput?.value || undefined,
        startDate: startInput?.value || undefined,
        endDate: endInput?.value || undefined
      })
      .subscribe({
        next: (res) => {
          row.editMode = false;
          row.editBusy = false;
          if (res.overAllocation) {
            this.assignError.set(
              `⚠ Resource "${res.resourceName}" bị over-allocation sau khi cập nhật (${Math.round(res.utilizationPercent ?? 0)}%).`
            );
          }
          this.reloadAllocations();
          this.changed.emit();
        },
        error: (err) => {
          row.editBusy = false;
          row.editError = err.message || 'Cập nhật allocation thất bại';
        }
      });
  }

  // ─── Workload ──────────────────────────────────────────────
  loadPlanWorkload(): void {
    this.workloadLoading.set(true);
    this.workloadError.set(null);
    this.planService
      .getPlanWorkload(this.planId, this.workloadFrom, this.workloadTo, this.workloadGranularity)
      .subscribe({
        next: (list) => {
          this.workload.set(list);
          this.workloadLoading.set(false);
        },
        error: (err) => {
          this.workloadLoading.set(false);
          this.workloadError.set(err.message || 'Không thể tải workload');
        }
      });
  }

  workloadTotals(): { demand: number; capacity: number | null } {
    let demand = 0;
    let capacity: number | null = 0;
    let anyCapacity = false;
    for (const w of this.workload()) {
      demand += w.totalDemandMinutes;
      if (w.totalCapacityMinutes != null) {
        capacity += w.totalCapacityMinutes;
        anyCapacity = true;
      }
    }
    return { demand, capacity: anyCapacity ? capacity : null };
  }

  utilizationText(percent?: number): string {
    return percent == null ? '—' : `${Math.round(percent)}%`;
  }

  // ─── Overview (cross-plan) ─────────────────────────────────
  loadOverview(): void {
    this.overviewLoading.set(true);
    this.overviewError.set(null);
    this.planService.getResourcesOverview(this.workloadFrom, this.workloadTo).subscribe({
      next: (list) => {
        this.overview.set(list);
        this.overviewLoading.set(false);
      },
      error: (err) => {
        this.overviewLoading.set(false);
        this.overviewError.set(err.message || 'Không thể tải tổng hợp resource');
      }
    });
  }

  openCapacity(row: ResourceOverviewRow): void {
    this.capacityRow = row;
    this.capacityError.set(null);
    this.capacityForm.reset({
      capacityPercent: row.capacityMinutes != null ? this.percentOf(row.capacityMinutes) : 100,
      startDate: this.workloadFrom,
      endDate: ''
    });
    this.showCapacityModal.set(true);
  }

  closeCapacity(): void {
    this.showCapacityModal.set(false);
    this.capacityRow = null;
  }

  private percentOf(capacityMinutes: number): number {
    const hours = capacityMinutes / 60;
    return Math.min(100, Math.max(0, Math.round((hours / 8) * 100)));
  }

  updateCapacity(): void {
    if (this.capacityForm.invalid || this.capacityBusy() || !this.capacityRow) {
      this.capacityForm.markAllAsTouched();
      return;
    }
    const v = this.capacityForm.value;
    this.capacityBusy.set(true);
    this.capacityError.set(null);
    this.planService
      .updateCapacity(this.capacityRow.resourceId, {
        resourceType: this.capacityRow.resourceType,
        capacityPercent: v.capacityPercent,
        startDate: v.startDate,
        endDate: v.endDate || undefined,
        source: 'PROJECT'
      })
      .subscribe({
        next: () => {
          this.capacityBusy.set(false);
          this.closeCapacity();
          this.loadOverview();
        },
        error: (err) => {
          this.capacityBusy.set(false);
          this.capacityError.set(err.message || 'Cập nhật capacity thất bại');
        }
      });
  }

  formatMinutes(min?: number): string {
    if (min == null) return '—';
    if (min < 60) return `${min} phút`;
    const hours = min / 60;
    if (hours < 24) return `${Math.round(hours)} giờ`;
    return `${Math.round(hours / 8)} ngày`;
  }
}