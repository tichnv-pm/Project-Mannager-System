import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { MilestoneService } from './milestone.service';
import { MilestoneResponse } from './milestone.model';
import { ProjectService } from '../projects/project.service';
import { ProjectOption } from '../dashboard/dashboard.model';

@Component({
  selector: 'app-milestone-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    StatusChipComponent,
    EmptyStateComponent,
    HasPermissionDirective
  ],
  templateUrl: './milestone-list.component.html',
  styleUrls: ['./milestone-list.component.scss']
})
export class MilestoneListComponent implements OnInit {
  private milestoneService = inject(MilestoneService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);

  milestones = signal<MilestoneResponse[]>([]);
  projectsList = signal<ProjectOption[]>([]);

  // Filter params
  selectedProjectId = '';
  selectedStatus = '';
  page = 0;
  size = 12;

  // Modal State
  showModal = signal(false);
  isEditMode = signal(false);
  editingMilestoneId: string | null = null;
  editingVersion = 0;
  formError = signal<string | null>(null);
  formSubmitting = signal(false);

  milestoneForm: FormGroup;

  constructor() {
    this.milestoneForm = this.fb.group({
      projectId: ['', [Validators.required]],
      name: ['', [Validators.required, Validators.maxLength(150)]],
      plannedDate: ['', [Validators.required]],
      actualDate: [''],
      status: ['NOT_STARTED'],
      progress: [0, [Validators.min(0), Validators.max(100)]],
      description: [''],
      note: ['']
    });
  }

  ngOnInit(): void {
    this.loadProjects();
    this.loadMilestones();
  }

  loadProjects(): void {
    this.projectService.getProjectsOptions().subscribe(prjs => this.projectsList.set(prjs));
  }

  loadMilestones(): void {
    this.loading.set(true);
    this.error.set(null);

    this.milestoneService.getMilestones(
      this.selectedProjectId || undefined,
      this.selectedStatus || undefined,
      this.page,
      this.size
    ).subscribe({
      next: (res) => {
        this.milestones.set(res.content || []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải danh sách Milestone');
      }
    });
  }

  onFilter(): void {
    this.page = 0;
    this.loadMilestones();
  }

  resetFilter(): void {
    this.selectedProjectId = '';
    this.selectedStatus = '';
    this.page = 0;
    this.loadMilestones();
  }

  openCreateModal(): void {
    this.isEditMode.set(false);
    this.editingMilestoneId = null;
    this.editingVersion = 0;
    this.formError.set(null);
    this.milestoneForm.reset({
      projectId: this.projectsList().length > 0 ? this.projectsList()[0].id : '',
      name: '',
      plannedDate: '',
      actualDate: '',
      status: 'NOT_STARTED',
      progress: 0,
      description: '',
      note: ''
    });
    this.showModal.set(true);
  }

  openEditModal(m: MilestoneResponse): void {
    this.isEditMode.set(true);
    this.editingMilestoneId = m.id;
    this.editingVersion = m.version;
    this.formError.set(null);

    this.milestoneForm.patchValue({
      projectId: m.projectId,
      name: m.name,
      plannedDate: m.plannedDate,
      actualDate: m.actualDate || '',
      status: m.status,
      progress: m.progress,
      description: m.description || '',
      note: m.note || ''
    });
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  saveMilestone(): void {
    if (this.milestoneForm.invalid || this.formSubmitting()) {
      this.milestoneForm.markAllAsTouched();
      return;
    }

    const formVal = this.milestoneForm.value;
    if (formVal.status === 'COMPLETED' && formVal.progress < 100) {
      this.formError.set('Trạng thái hoàn thành (COMPLETED) bắt buộc tiến độ phải đạt 100%');
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);

    if (this.isEditMode() && this.editingMilestoneId) {
      const req = { ...formVal, version: this.editingVersion };
      this.milestoneService.updateMilestone(this.editingMilestoneId, req).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadMilestones();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Cập nhật Milestone thất bại');
        }
      });
    } else {
      this.milestoneService.createMilestone(formVal).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadMilestones();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Tạo Milestone thất bại');
        }
      });
    }
  }

  completeMilestone(m: MilestoneResponse): void {
    if (!confirm(`Xác nhận đánh dấu mốc "${m.name}" là HOÀN THÀNH (COMPLETED 100%)?`)) return;

    const updateReq = {
      name: m.name,
      description: m.description,
      plannedDate: m.plannedDate,
      actualDate: new Date().toISOString().slice(0, 10),
      status: 'COMPLETED' as any,
      progress: 100,
      note: m.note,
      version: m.version
    };

    this.milestoneService.updateMilestone(m.id, updateReq).subscribe({
      next: () => this.loadMilestones(),
      error: (err) => alert(err.message || 'Cập nhật mốc hoàn thành thất bại')
    });
  }

  deleteMilestone(m: MilestoneResponse): void {
    if (!confirm(`Bạn có chắc muốn xóa mốc "${m.name}"?`)) return;
    this.milestoneService.deleteMilestone(m.id).subscribe({
      next: () => this.loadMilestones(),
      error: (err) => alert(err.message || 'Xóa Milestone thất bại')
    });
  }
}
