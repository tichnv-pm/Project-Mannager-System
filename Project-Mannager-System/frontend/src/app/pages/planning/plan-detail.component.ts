import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Observable } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectService } from '../projects/project.service';
import { PlanCalendarComponent } from './plan-calendar.component';
import { PlanChangeComponent } from './plan-change.component';
import { PlanDependencyEditorComponent } from './plan-dependency-editor.component';
import { PlanGanttComponent } from './plan-gantt.component';
import { PlanResourceComponent } from './plan-resource.component';
import { PlanSchedulingComponent } from './plan-scheduling.component';
import { PlanWbsEditorComponent } from './plan-wbs-editor.component';
import { PlanService } from './plan.service';
import { PlanVersionBaselineComponent } from './plan-version-baseline.component';
import { PLAN_TYPE_LABELS, PlanResponse } from './plan.model';

@Component({
  selector: 'app-plan-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    PageHeaderComponent,
    StatusChipComponent,
    EmptyStateComponent,
    HasPermissionDirective,
    PlanCalendarComponent,
    PlanChangeComponent,
    PlanDependencyEditorComponent,
    PlanGanttComponent,
    PlanResourceComponent,
    PlanSchedulingComponent,
    PlanVersionBaselineComponent,
    PlanWbsEditorComponent
  ],
  templateUrl: './plan-detail.component.html',
  styleUrls: ['./plan-detail.component.scss']
})
export class PlanDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private planService = inject(PlanService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);

  planId: string | null = null;
  plan = signal<PlanResponse | null>(null);
  projectName = signal<string | null>(null);
  masterPlan = signal<PlanResponse | null>(null);
  childPlans = signal<PlanResponse[]>([]);
  activeTab: 'overview' | 'wbs' | 'dependencies' | 'calendar' | 'scheduling' | 'resource' | 'versions' | 'change' | 'gantt' = 'overview';

  loading = signal(true);
  error = signal<string | null>(null);
  lifecycleBusy = signal(false);

  // Edit Modal State
  showEditModal = signal(false);
  formError = signal<string | null>(null);
  formSubmitting = signal(false);

  // Delete Confirm Modal
  showDeleteModal = signal(false);
  deleteMessage = signal('');

  editForm: FormGroup;

  constructor() {
    this.editForm = this.fb.group({
      planName: ['', [Validators.required, Validators.maxLength(200)]],
      plannedStart: [''],
      plannedFinish: [''],
      description: ['']
    });
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.planId = params.get('id');
      if (this.planId) {
        this.masterPlan.set(null);
        this.childPlans.set([]);
        this.activeTab = 'overview';
        this.loadPlan();
      }
    });
  }

  loadPlan(): void {
    if (!this.planId) return;
    this.loading.set(true);
    this.error.set(null);

    this.planService.getPlan(this.planId).subscribe({
      next: (p) => {
        this.plan.set(p);
        this.loading.set(false);
        this.loadRelated(p);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải chi tiết kế hoạch');
      }
    });
  }

  private loadRelated(p: PlanResponse): void {
    this.projectService.getProjectsOptions().subscribe(prjs => {
      const prj = prjs.find(x => x.id === p.projectId);
      this.projectName.set(prj ? `${prj.code} — ${prj.name}` : p.projectId);
    });

    if (p.planType === 'DETAIL' && p.parentPlanId) {
      this.planService.getPlan(p.parentPlanId).subscribe({
        next: parent => this.masterPlan.set(parent),
        error: () => this.masterPlan.set(null)
      });
    }

    if (p.planType === 'MASTER') {
      this.planService.getPlans(undefined, p.projectId, 'DETAIL', undefined, 0, 100).subscribe({
        next: res => this.childPlans.set(res.content.filter(c => c.parentPlanId === p.id)),
        error: () => this.childPlans.set([])
      });
    }
  }

  typeLabel(type: string): string {
    return PLAN_TYPE_LABELS[type as keyof typeof PLAN_TYPE_LABELS] || type;
  }

  formatDuration(minutes?: number): string {
    if (minutes == null) return 'N/A';
    if (minutes < 60) return `${minutes} phút`;
    const hours = minutes / 60;
    if (hours < 24) return `${Math.round(hours)} giờ`;
    return `${Math.round(hours / 8)} ngày làm việc`;
  }

  onWbsChanged(): void {
    if (this.planId) {
      this.planService.getPlan(this.planId).subscribe({
        next: p => this.plan.set(p),
        error: () => {}
      });
    }
  }

  // ─── Lifecycle ────────────────────────────────────────────────
  submitPlan(): void {
    this.runLifecycle(() => this.planService.submitPlan(this.plan()!.id));
  }

  approvePlan(): void {
    this.runLifecycle(() => this.planService.approvePlan(this.plan()!.id));
  }

  activatePlan(): void {
    this.runLifecycle(() => this.planService.activatePlan(this.plan()!.id));
  }

  private runLifecycle(action: () => Observable<PlanResponse>): void {
    if (!this.plan()) return;
    this.lifecycleBusy.set(true);
    action().subscribe({
      next: (updated) => {
        this.lifecycleBusy.set(false);
        this.plan.set(updated);
        this.loadRelated(updated);
      },
      error: (err) => {
        this.lifecycleBusy.set(false);
        alert(err.message || 'Thao tác vòng đời kế hoạch thất bại');
      }
    });
  }

  // ─── Edit ─────────────────────────────────────────────────────
  openEditModal(): void {
    const p = this.plan();
    if (!p) return;
    this.formError.set(null);
    this.editForm.patchValue({
      planName: p.planName,
      plannedStart: p.plannedStart || '',
      plannedFinish: p.plannedFinish || '',
      description: p.description || ''
    });
    this.showEditModal.set(true);
  }

  closeEditModal(): void {
    this.showEditModal.set(false);
  }

  savePlan(): void {
    const p = this.plan();
    if (!p || this.editForm.invalid || this.formSubmitting()) {
      this.editForm.markAllAsTouched();
      return;
    }

    const formVal = this.editForm.value;
    if (formVal.plannedStart && formVal.plannedFinish && formVal.plannedFinish < formVal.plannedStart) {
      this.formError.set('Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu');
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);

    const updateReq = {
      planName: formVal.planName,
      description: formVal.description || undefined,
      plannedStart: formVal.plannedStart || undefined,
      plannedFinish: formVal.plannedFinish || undefined,
      version: p.version
    };
    this.planService.updatePlan(p.id, updateReq).subscribe({
      next: (updated) => {
        this.formSubmitting.set(false);
        this.closeEditModal();
        this.plan.set(updated);
        this.loadRelated(updated);
      },
      error: (err) => {
        this.formSubmitting.set(false);
        this.formError.set(err.message || 'Cập nhật kế hoạch thất bại');
      }
    });
  }

  // ─── Delete ───────────────────────────────────────────────────
  openDeleteModal(): void {
    const p = this.plan();
    if (!p) return;
    this.deleteMessage.set(`Xóa kế hoạch "${p.planName}" (${p.planCode})? Hành động này không thể hoàn tác.`);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
  }

  confirmDelete(): void {
    const p = this.plan();
    if (!p) return;
    this.planService.deletePlan(p.id).subscribe({
      next: () => {
        this.closeDeleteModal();
        this.router.navigate(['/plans']);
      },
      error: (err) => {
        alert(err.message || 'Xóa kế hoạch thất bại');
      }
    });
  }
}