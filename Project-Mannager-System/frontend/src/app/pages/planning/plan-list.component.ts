import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Observable } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectService } from '../projects/project.service';
import { ProjectOption } from '../dashboard/dashboard.model';
import { PlanService } from './plan.service';
import { PLAN_TYPE_LABELS, PlanResponse, PlanStatus, PlanTemplateResponse } from './plan.model';

@Component({
  selector: 'app-plan-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    PageHeaderComponent,
    StatusChipComponent,
    EmptyStateComponent,
    HasPermissionDirective
  ],
  templateUrl: './plan-list.component.html',
  styleUrls: ['./plan-list.component.scss']
})
export class PlanListComponent implements OnInit {
  private planService = inject(PlanService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);

  currentParentMilestoneTaskId: string | null = null;

  loading = signal(true);
  error = signal<string | null>(null);

  plans = signal<PlanResponse[]>([]);
  projectsList = signal<ProjectOption[]>([]);
  masterPlans = signal<PlanResponse[]>([]);
  totalElements = signal(0);
  page = 0;
  size = 12;

  // Filter params
  keyword = '';
  selectedProjectId = '';
  selectedPlanType = '';
  selectedStatus = '';

  statusOptions: { value: PlanStatus; label: string }[] = [
    { value: 'DRAFT', label: 'Nháp (DRAFT)' },
    { value: 'SUBMITTED', label: 'Chờ duyệt (SUBMITTED)' },
    { value: 'APPROVED', label: 'Đã duyệt (APPROVED)' },
    { value: 'ACTIVE', label: 'Đang hiệu lực (ACTIVE)' },
    { value: 'ON_HOLD', label: 'Tạm dừng (ON_HOLD)' },
    { value: 'COMPLETED', label: 'Hoàn tất (COMPLETED)' },
    { value: 'CANCELLED', label: 'Đã hủy (CANCELLED)' },
    { value: 'ARCHIVED', label: 'Đã lưu trữ (ARCHIVED)' }
  ];

  // Lifecycle state
  lifecycleBusy: Record<string, boolean> = {};

  // Modal State
  showModal = signal(false);
  isEditMode = signal(false);
  editingPlanId: string | null = null;
  editingVersion = 0;
  formError = signal<string | null>(null);
  formSubmitting = signal(false);

  // Delete Confirm Modal
  showDeleteModal = signal(false);
  deletingPlan: PlanResponse | null = null;
  deleteMessage = signal('');

  planForm: FormGroup;

  // Template Modal State
  templates = signal<PlanTemplateResponse[]>([]);
  showTemplateModal = signal(false);
  selectedTemplate = signal<PlanTemplateResponse | null>(null);
  showApplyTemplateModal = signal(false);
  applyTemplateForm: FormGroup;
  templateLoading = signal(false);

  constructor() {
    this.planForm = this.fb.group({
      projectId: ['', [Validators.required]],
      planType: ['MASTER', [Validators.required]],
      planCode: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
      parentPlanId: [''],
      templateId: [''],
      planName: ['', [Validators.required, Validators.maxLength(200)]],
      plannedStart: [''],
      plannedFinish: [''],
      description: ['']
    });

    this.applyTemplateForm = this.fb.group({
      projectId: ['', [Validators.required]],
      planCode: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
      planName: ['', [Validators.required, Validators.maxLength(200)]],
      startDate: [new Date().toISOString().substring(0, 10), [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.loadProjects();
    this.loadPlans();
    this.loadTemplates();

    this.route.queryParams.subscribe(params => {
      if (params['action'] === 'create') {
        const projectId = params['projectId'] || '';
        const parentPlanId = params['parentPlanId'] || '';
        const parentMilestoneTaskId = params['parentMilestoneTaskId'] || '';
        this.openCreateChildPlan(projectId, parentPlanId, parentMilestoneTaskId);
      }
    });
  }

  openCreateChildPlan(projectId: string, parentPlanId: string, parentMilestoneTaskId: string): void {
    this.isEditMode.set(false);
    this.editingPlanId = null;
    this.editingVersion = 0;
    this.formError.set(null);
    this.planForm.reset({
      projectId: projectId,
      planType: 'DETAIL',
      planCode: '',
      parentPlanId: parentPlanId,
      planName: '',
      plannedStart: '',
      plannedFinish: '',
      description: ''
    });
    this.currentParentMilestoneTaskId = parentMilestoneTaskId;
    this.masterPlans.set([]);
    this.planService.getPlans(undefined, projectId, 'MASTER', undefined, 0, 100).subscribe({
      next: res => {
        this.masterPlans.set(res.content.filter(m => m.status !== 'CANCELLED' && m.status !== 'ARCHIVED'));
        this.planForm.patchValue({ parentPlanId: parentPlanId });
      },
      error: () => this.masterPlans.set([])
    });
    this.showModal.set(true);
  }

  loadTemplates(): void {
    this.templateLoading.set(true);
    this.planService.getTemplates().subscribe({
      next: (tpls) => {
        this.templates.set(tpls);
        this.templateLoading.set(false);
      },
      error: () => this.templateLoading.set(false)
    });
  }

  openTemplateModal(): void {
    this.showTemplateModal.set(true);
    if (this.templates().length === 0) {
      this.loadTemplates();
    }
  }

  closeTemplateModal(): void {
    this.showTemplateModal.set(false);
  }

  openApplyTemplateModal(tpl: PlanTemplateResponse): void {
    this.selectedTemplate.set(tpl);
    this.applyTemplateForm.patchValue({
      projectId: this.selectedProjectId || '',
      planCode: `PLN-${tpl.templateCode}-01`,
      planName: `Kế hoạch theo mẫu ${tpl.templateName}`,
      startDate: new Date().toISOString().substring(0, 10)
    });
    this.showApplyTemplateModal.set(true);
  }

  closeApplyTemplateModal(): void {
    this.showApplyTemplateModal.set(false);
    this.selectedTemplate.set(null);
  }

  submitApplyTemplate(): void {
    if (this.applyTemplateForm.invalid || !this.selectedTemplate()) {
      this.applyTemplateForm.markAllAsTouched();
      return;
    }
    const val = this.applyTemplateForm.value;
    const req = {
      projectId: val.projectId,
      templateId: this.selectedTemplate()!.id,
      planCode: val.planCode,
      planName: val.planName,
      startDate: val.startDate,
      planType: 'MASTER' as const
    };

    this.formSubmitting.set(true);
    this.planService.createPlanFromTemplate(req).subscribe({
      next: () => {
        this.formSubmitting.set(false);
        this.closeApplyTemplateModal();
        this.closeTemplateModal();
        this.loadPlans();
      },
      error: (err) => {
        this.formSubmitting.set(false);
        alert(err.message || 'Tạo kế hoạch từ Template thất bại');
      }
    });
  }

  loadProjects(): void {
    this.projectService.getProjectsOptions().subscribe(prjs => this.projectsList.set(prjs));
  }

  loadPlans(): void {
    this.loading.set(true);
    this.error.set(null);

    this.planService.getPlans(
      this.keyword || undefined,
      this.selectedProjectId || undefined,
      (this.selectedPlanType as never) || undefined,
      (this.selectedStatus as never) || undefined,
      this.page,
      this.size
    ).subscribe({
      next: (res) => {
        this.plans.set(res.content);
        this.totalElements.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải danh sách kế hoạch');
      }
    });
  }

  onFilter(): void {
    this.page = 0;
    this.loadPlans();
  }

  resetFilter(): void {
    this.keyword = '';
    this.selectedProjectId = '';
    this.selectedPlanType = '';
    this.selectedStatus = '';
    this.page = 0;
    this.loadPlans();
  }

  goToPage(p: number): void {
    this.page = p;
    this.loadPlans();
  }

  totalPages(): number {
    return Math.max(1, Math.ceil(this.totalElements() / this.size));
  }

  hasNextPage(): boolean {
    return (this.page + 1) * this.size < this.totalElements();
  }

  typeLabel(type: string): string {
    return PLAN_TYPE_LABELS[type as keyof typeof PLAN_TYPE_LABELS] || type;
  }

  projectLabel(projectId: string): string {
    const p = this.projectsList().find(x => x.id === projectId);
    return p ? `${p.code} — ${p.name}` : projectId;
  }

  // ─── Lifecycle ────────────────────────────────────────────────
  submitPlan(p: PlanResponse): void {
    this.setBusy(p.id, () => this.planService.submitPlan(p.id));
  }

  approvePlan(p: PlanResponse): void {
    this.setBusy(p.id, () => this.planService.approvePlan(p.id));
  }

  activatePlan(p: PlanResponse): void {
    this.setBusy(p.id, () => this.planService.activatePlan(p.id));
  }

  private setBusy(planId: string, action: () => Observable<PlanResponse>): void {
    this.lifecycleBusy[planId] = true;
    action().subscribe({
      next: () => {
        this.lifecycleBusy[planId] = false;
        this.loadPlans();
      },
      error: (err) => {
        this.lifecycleBusy[planId] = false;
        alert(err.message || 'Thao tác vòng đời kế hoạch thất bại');
      }
    });
  }

  // ─── Create / Edit ────────────────────────────────────────────
  openCreateModal(): void {
    this.isEditMode.set(false);
    this.editingPlanId = null;
    this.editingVersion = 0;
    this.currentParentMilestoneTaskId = null;
    this.formError.set(null);
    this.planForm.reset({
      projectId: '',
      planType: 'MASTER',
      planCode: '',
      parentPlanId: '',
      planName: '',
      plannedStart: '',
      plannedFinish: '',
      description: ''
    });
    this.masterPlans.set([]);
    this.showModal.set(true);
  }

  openEditModal(plan: PlanResponse): void {
    this.isEditMode.set(true);
    this.editingPlanId = plan.id;
    this.editingVersion = plan.version;
    this.formError.set(null);
    this.planForm.patchValue({
      projectId: plan.projectId,
      planType: plan.planType,
      planCode: plan.planCode,
      parentPlanId: plan.parentPlanId || '',
      planName: plan.planName,
      plannedStart: plan.plannedStart || '',
      plannedFinish: plan.plannedFinish || '',
      description: plan.description || ''
    });
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  onProjectChange(): void {
    this.masterPlans.set([]);
    this.planForm.patchValue({ parentPlanId: '' });
    const projectId = this.planForm.get('projectId')?.value;
    if (projectId) {
      this.planService.getPlans(undefined, projectId, 'MASTER', undefined, 0, 100).subscribe({
        next: res => this.masterPlans.set(res.content.filter(m => m.status !== 'CANCELLED' && m.status !== 'ARCHIVED')),
        error: () => this.masterPlans.set([])
      });
    }
  }

  onPlanTypeChange(): void {
    const planType = this.planForm.get('planType')?.value;
    const parentControl = this.planForm.get('parentPlanId');
    if (planType === 'DETAIL') {
      parentControl?.setValidators([Validators.required]);
    } else {
      parentControl?.clearValidators();
      parentControl?.patchValue('');
    }
    parentControl?.updateValueAndValidity();
  }

  savePlan(): void {
    if (this.planForm.invalid || this.formSubmitting()) {
      this.planForm.markAllAsTouched();
      return;
    }

    const formVal = this.planForm.value;
    if (formVal.plannedStart && formVal.plannedFinish && formVal.plannedFinish < formVal.plannedStart) {
      this.formError.set('Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu');
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);

    if (this.isEditMode() && this.editingPlanId) {
      const updateReq = {
        planName: formVal.planName,
        description: formVal.description || undefined,
        plannedStart: formVal.plannedStart || undefined,
        plannedFinish: formVal.plannedFinish || undefined,
        version: this.editingVersion
      };
      this.planService.updatePlan(this.editingPlanId, updateReq).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadPlans();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Cập nhật kế hoạch thất bại');
        }
      });
    } else {
      const createReq = {
        projectId: formVal.projectId,
        planCode: formVal.planCode,
        planName: formVal.planName,
        planType: formVal.planType,
        parentPlanId: formVal.planType === 'DETAIL' ? formVal.parentPlanId || undefined : undefined,
        parentMilestoneTaskId: formVal.planType === 'DETAIL' ? this.currentParentMilestoneTaskId || undefined : undefined,
        plannedStart: formVal.plannedStart || undefined,
        plannedFinish: formVal.plannedFinish || undefined,
        description: formVal.description || undefined
      };
      this.planService.createPlan(createReq).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadPlans();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Tạo kế hoạch thất bại');
        }
      });
    }
  }

  // ─── Delete ───────────────────────────────────────────────────
  openDeleteModal(plan: PlanResponse): void {
    this.deletingPlan = plan;
    this.deleteMessage.set(`Bạn có chắc chắn muốn xóa kế hoạch "${plan.planName}" (${plan.planCode})?`);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deletingPlan = null;
  }

  confirmDelete(): void {
    if (!this.deletingPlan) return;

    this.planService.deletePlan(this.deletingPlan.id).subscribe({
      next: () => {
        this.closeDeleteModal();
        this.loadPlans();
      },
      error: (err) => {
        alert(err.message || 'Xóa kế hoạch thất bại');
      }
    });
  }
}