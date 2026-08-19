import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectService } from '../projects/project.service';
import { PlanService } from './plan.service';
import {
  PlanTemplateDetail,
  PlanTemplateResponse,
  PlanTemplateTask,
  TEMPLATE_STATUS_LABELS,
  TEMPLATE_TYPE_LABELS,
  PLAN_TYPE_LABELS,
  PLAN_TASK_TYPE_LABELS
} from './plan.model';

interface ProjectOption {
  id: string;
  code: string;
  name: string;
}

@Component({
  selector: 'app-plan-template',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, PageHeaderComponent, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './plan-template.component.html',
  styleUrls: ['./plan-template.component.scss']
})
export class PlanTemplateComponent implements OnInit {
  private planService = inject(PlanService);
  private projectService = inject(ProjectService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);
  templates = signal<PlanTemplateResponse[]>([]);

  // detail modal
  showDetail = signal(false);
  detail = signal<PlanTemplateDetail | null>(null);
  detailLoading = signal(false);
  detailError = signal<string | null>(null);

  // create-from-template modal
  showCreateModal = signal(false);
  createError = signal<string | null>(null);
  createBusy = signal(false);
  projects = signal<ProjectOption[]>([]);
  masterPlans = signal<{ id: string; planCode: string; planName: string }[]>([]);
  selectedTemplate: PlanTemplateResponse | null = null;
  createForm: FormGroup;

  readonly typeLabels = TEMPLATE_TYPE_LABELS;
  readonly statusLabels = TEMPLATE_STATUS_LABELS;
  readonly planTypeLabels = PLAN_TYPE_LABELS;
  readonly taskTypeLabels = PLAN_TASK_TYPE_LABELS;

  constructor() {
    this.createForm = this.fb.group({
      projectId: ['', Validators.required],
      planCode: ['', [Validators.required, Validators.maxLength(50)]],
      planName: ['', [Validators.required, Validators.maxLength(200)]],
      planType: ['MASTER'],
      parentPlanId: [''],
      startDate: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planService.getTemplates().subscribe({
      next: (templates) => {
        this.templates.set(templates);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải danh sách template');
      }
    });
  }

  // ─── Detail ────────────────────────────────────────────────────
  openDetail(t: PlanTemplateResponse): void {
    this.detail.set(null);
    this.showDetail.set(true);
    this.detailLoading.set(true);
    this.detailError.set(null);
    this.planService.getTemplateDetail(t.id).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.detailLoading.set(false);
      },
      error: (err) => {
        this.detailLoading.set(false);
        this.detailError.set(err.message || 'Không thể tải chi tiết template');
      }
    });
  }

  closeDetail(): void {
    this.showDetail.set(false);
  }

  taskRows(): PlanTemplateTask[] {
    const d = this.detail();
    if (!d) return [];
    const byParent = new Map<string | undefined, PlanTemplateTask[]>();
    for (const t of d.tasks) {
      const key = t.parentId ?? undefined;
      if (!byParent.has(key)) byParent.set(key, []);
      byParent.get(key)!.push(t);
    }
    for (const list of byParent.values()) {
      list.sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0));
    }
    const out: PlanTemplateTask[] = [];
    const walk = (parentId?: string, depth = 0) => {
      for (const t of byParent.get(parentId) || []) {
        out.push({ ...t, wbsCode: `${t.wbsCode}` });
        walk(t.id, depth + 1);
      }
    };
    walk(undefined);
    return out;
  }

  // ─── Create plan from template ─────────────────────────────────
  openCreate(t: PlanTemplateResponse): void {
    this.createError.set(null);
    this.masterPlans.set([]);
    this.createForm.reset({
      planCode: '',
      planName: t.templateName,
      planType: 'MASTER',
      parentPlanId: '',
      startDate: ''
    });
    this.createForm.patchValue({ projectId: '' });
    this.selectedTemplate = t;
    this.projectService.getProjectsOptions().subscribe({
      next: (prjs) => {
        this.projects.set(prjs.map(p => ({ id: p.id, code: p.code, name: p.name })));
      },
      error: () => this.projects.set([])
    });
    this.showCreateModal.set(true);
  }

  closeCreate(): void {
    this.showCreateModal.set(false);
  }

  onProjectChange(): void {
    const projectId = this.createForm.value.projectId;
    this.masterPlans.set([]);
    this.createForm.patchValue({ parentPlanId: '', planType: 'MASTER' });
    if (!projectId) return;
    this.planService.getPlans(undefined, projectId, 'MASTER', undefined, 0, 100).subscribe({
      next: (res) => {
        this.masterPlans.set(res.content.map(p => ({ id: p.id, planCode: p.planCode, planName: p.planName })));
      },
      error: () => this.masterPlans.set([])
    });
  }

  onPlanTypeChange(): void {
    if (this.createForm.value.planType !== 'DETAIL') {
      this.createForm.patchValue({ parentPlanId: '' });
    }
  }

  createPlan(): void {
    const f = this.createForm.value;
    if (this.createForm.invalid || this.createBusy() || !this.selectedTemplate) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.createBusy.set(true);
    this.createError.set(null);
    this.planService.createPlanFromTemplate({
      projectId: f.projectId,
      templateId: this.selectedTemplate.id,
      planCode: f.planCode,
      planName: f.planName,
      planType: f.planType,
      ...(f.planType === 'DETAIL' && f.parentPlanId ? { parentPlanId: f.parentPlanId } : {}),
      startDate: f.startDate
    }).subscribe({
      next: (plan) => {
        this.createBusy.set(false);
        this.showCreateModal.set(false);
        this.router.navigate(['/plans', plan.id]);
      },
      error: (err) => {
        this.createBusy.set(false);
        this.createError.set(err.message || 'Tạo plan từ template thất bại');
      }
    });
  }
}