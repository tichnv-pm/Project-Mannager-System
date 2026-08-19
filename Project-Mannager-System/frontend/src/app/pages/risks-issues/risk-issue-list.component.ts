import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { PriorityChipComponent } from '../../shared/components/priority-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { RiskIssueService } from './risk-issue.service';
import { IssueResponse, RiskResponse } from './risk-issue.model';
import { ProjectService } from '../projects/project.service';
import { ProjectOption } from '../dashboard/dashboard.model';
import { UserBrief } from '../../core/models/auth.model';

@Component({
  selector: 'app-risk-issue-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    StatusChipComponent,
    PriorityChipComponent,
    EmptyStateComponent
  ],
  templateUrl: './risk-issue-list.component.html',
  styleUrls: ['./risk-issue-list.component.scss']
})
export class RiskIssueListComponent implements OnInit {
  private riskIssueService = inject(RiskIssueService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);

  activeTab: 'risks' | 'issues' = 'risks';

  risks = signal<RiskResponse[]>([]);
  issues = signal<IssueResponse[]>([]);
  projectsList = signal<ProjectOption[]>([]);
  usersList = signal<UserBrief[]>([]);

  // Filter params
  keyword = '';
  selectedProjectId = '';
  selectedStatus = '';
  selectedLevel = '';
  page = 0;
  size = 12;

  // Modals
  showRiskModal = signal(false);
  isEditRisk = signal(false);
  editingRiskId: string | null = null;

  showIssueModal = signal(false);
  isEditIssue = signal(false);
  editingIssueId: string | null = null;

  formError = signal<string | null>(null);
  formSubmitting = signal(false);

  riskForm: FormGroup;
  issueForm: FormGroup;

  constructor() {
    this.riskForm = this.fb.group({
      projectId: ['', [Validators.required]],
      title: ['', [Validators.required, Validators.maxLength(200)]],
      probability: ['MEDIUM', [Validators.required]],
      impact: ['MEDIUM', [Validators.required]],
      ownerId: ['', [Validators.required]],
      mitigationPlan: [''],
      contingencyPlan: [''],
      status: ['OPEN'],
      description: ['']
    });

    this.issueForm = this.fb.group({
      projectId: ['', [Validators.required]],
      title: ['', [Validators.required, Validators.maxLength(200)]],
      severity: ['HIGH', [Validators.required]],
      ownerId: ['', [Validators.required]],
      rootCause: [''],
      solution: [''],
      status: ['OPEN'],
      description: ['']
    });
  }

  ngOnInit(): void {
    this.loadOptions();
    this.loadData();
  }

  loadOptions(): void {
    this.projectService.getProjectsOptions().subscribe(prjs => this.projectsList.set(prjs));
    this.projectService.getUsersList().subscribe(users => this.usersList.set(users));
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);

    if (this.activeTab === 'risks') {
      this.riskIssueService.getRisks(
        this.keyword || undefined,
        this.selectedProjectId || undefined,
        this.selectedStatus || undefined,
        this.selectedLevel || undefined,
        this.page,
        this.size
      ).subscribe({
        next: (res) => {
          this.risks.set(res.content || []);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.message || 'Không thể tải danh sách Risk');
        }
      });
    } else {
      this.riskIssueService.getIssues(
        this.keyword || undefined,
        this.selectedProjectId || undefined,
        this.selectedStatus || undefined,
        this.selectedLevel || undefined,
        this.page,
        this.size
      ).subscribe({
        next: (res) => {
          this.issues.set(res.content || []);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.message || 'Không thể tải danh sách Issue');
        }
      });
    }
  }

  switchTab(tab: 'risks' | 'issues'): void {
    this.activeTab = tab;
    this.resetFilter();
  }

  onFilter(): void {
    this.page = 0;
    this.loadData();
  }

  resetFilter(): void {
    this.keyword = '';
    this.selectedProjectId = '';
    this.selectedStatus = '';
    this.selectedLevel = '';
    this.page = 0;
    this.loadData();
  }

  openCreateRiskModal(): void {
    this.isEditRisk.set(false);
    this.editingRiskId = null;
    this.formError.set(null);
    this.riskForm.reset({
      projectId: this.projectsList().length > 0 ? this.projectsList()[0].id : '',
      title: '',
      probability: 'MEDIUM',
      impact: 'MEDIUM',
      ownerId: this.usersList().length > 0 ? this.usersList()[0].id : '',
      mitigationPlan: '',
      contingencyPlan: '',
      status: 'OPEN',
      description: ''
    });
    this.showRiskModal.set(true);
  }

  openEditRiskModal(r: RiskResponse): void {
    this.isEditRisk.set(true);
    this.editingRiskId = r.id;
    this.formError.set(null);

    this.riskForm.patchValue({
      projectId: r.projectId,
      title: r.title,
      probability: r.probability,
      impact: r.impact,
      ownerId: r.owner?.id || '',
      mitigationPlan: r.mitigationPlan || '',
      contingencyPlan: r.contingencyPlan || '',
      status: r.status,
      description: r.description || ''
    });
    this.showRiskModal.set(true);
  }

  saveRisk(): void {
    if (this.riskForm.invalid || this.formSubmitting()) {
      this.riskForm.markAllAsTouched();
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);
    const val = this.riskForm.value;

    if (this.isEditRisk() && this.editingRiskId) {
      this.riskIssueService.updateRisk(this.editingRiskId, val).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.showRiskModal.set(false);
          this.loadData();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Cập nhật Risk thất bại');
        }
      });
    } else {
      this.riskIssueService.createRisk(val).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.showRiskModal.set(false);
          this.loadData();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Tạo Risk thất bại');
        }
      });
    }
  }

  convertToIssue(r: RiskResponse): void {
    if (!confirm(`Bạn có chắc muốn chuyển Rủi ro "${r.title}" (${r.code}) thành Vấn đề phát sinh (Issue)?`)) return;

    this.riskIssueService.convertToIssue(r.id).subscribe({
      next: () => {
        alert('Chuyển đổi thành công!');
        this.switchTab('issues');
      },
      error: (err) => alert(err.message || 'Chuyển đổi sang Issue thất bại')
    });
  }

  deleteRisk(r: RiskResponse): void {
    if (!confirm(`Bạn có chắc muốn xóa Rủi ro "${r.title}"?`)) return;
    this.riskIssueService.deleteRisk(r.id).subscribe({
      next: () => this.loadData(),
      error: (err) => alert(err.message || 'Xóa Risk thất bại')
    });
  }

  // ISSUE METHODS
  openCreateIssueModal(): void {
    this.isEditIssue.set(false);
    this.editingIssueId = null;
    this.formError.set(null);
    this.issueForm.reset({
      projectId: this.projectsList().length > 0 ? this.projectsList()[0].id : '',
      title: '',
      severity: 'HIGH',
      ownerId: this.usersList().length > 0 ? this.usersList()[0].id : '',
      rootCause: '',
      solution: '',
      status: 'OPEN',
      description: ''
    });
    this.showIssueModal.set(true);
  }

  openEditIssueModal(i: IssueResponse): void {
    this.isEditIssue.set(true);
    this.editingIssueId = i.id;
    this.formError.set(null);

    this.issueForm.patchValue({
      projectId: i.projectId,
      title: i.title,
      severity: i.severity,
      ownerId: i.owner?.id || '',
      rootCause: i.rootCause || '',
      solution: i.solution || '',
      status: i.status,
      description: i.description || ''
    });
    this.showIssueModal.set(true);
  }

  saveIssue(): void {
    if (this.issueForm.invalid || this.formSubmitting()) {
      this.issueForm.markAllAsTouched();
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);
    const val = this.issueForm.value;

    if (this.isEditIssue() && this.editingIssueId) {
      this.riskIssueService.updateIssue(this.editingIssueId, val).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.showIssueModal.set(false);
          this.loadData();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Cập nhật Issue thất bại');
        }
      });
    } else {
      this.riskIssueService.createIssue(val).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.showIssueModal.set(false);
          this.loadData();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Tạo Issue thất bại');
        }
      });
    }
  }

  resolveIssue(i: IssueResponse): void {
    if (!confirm(`Đánh dấu Vấn đề "${i.title}" là ĐÃ GIẢI QUYẾT (RESOLVED)?`)) return;
    const req = {
      projectId: i.projectId,
      title: i.title,
      severity: i.severity,
      ownerId: i.owner?.id || '',
      status: 'RESOLVED' as any
    };

    this.riskIssueService.updateIssue(i.id, req).subscribe({
      next: () => this.loadData(),
      error: (err) => alert(err.message || 'Giải quyết Issue thất bại')
    });
  }

  deleteIssue(i: IssueResponse): void {
    if (!confirm(`Bạn có chắc muốn xóa Vấn đề "${i.title}"?`)) return;
    this.riskIssueService.deleteIssue(i.id).subscribe({
      next: () => this.loadData(),
      error: (err) => alert(err.message || 'Xóa Issue thất bại')
    });
  }
}
