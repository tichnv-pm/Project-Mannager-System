import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectService } from './project.service';
import { ProjectResponse, ProjectStatus } from './project.model';
import { UserBrief } from '../../core/models/auth.model';

@Component({
  selector: 'app-project-list',
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
  templateUrl: './project-list.component.html',
  styleUrls: ['./project-list.component.scss']
})
export class ProjectListComponent implements OnInit {
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);

  projects = signal<ProjectResponse[]>([]);
  totalElements = signal(0);
  usersList = signal<UserBrief[]>([]);

  // Filter params
  keyword = '';
  selectedStatus = '';
  myOnly = false;
  page = 0;
  size = 12;

  // Modal State
  showModal = signal(false);
  isEditMode = signal(false);
  editingProjectId: string | null = null;
  editingVersion = 0;
  formError = signal<string | null>(null);
  formSubmitting = signal(false);

  // Delete Confirm Modal
  showDeleteModal = signal(false);
  deletingProject: ProjectResponse | null = null;
  needsDeleteConfirm = signal(false);
  deleteMessage = signal('');

  projectForm: FormGroup;

  constructor() {
    this.projectForm = this.fb.group({
      code: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20)]],
      name: ['', [Validators.required, Validators.maxLength(100)]],
      customerName: [''],
      projectManagerId: [''],
      startDate: [''],
      endDate: [''],
      description: [''],
      status: ['PLANNING']
    });
  }

  ngOnInit(): void {
    this.loadUsers();
    this.loadProjects();
  }

  loadUsers(): void {
    this.projectService.getUsersList().subscribe(users => {
      this.usersList.set(users);
    });
  }

  loadProjects(): void {
    this.loading.set(true);
    this.error.set(null);

    this.projectService.getProjects(
      this.keyword || undefined,
      this.selectedStatus || undefined,
      this.myOnly || undefined,
      this.page,
      this.size
    ).subscribe({
      next: (res) => {
        this.projects.set(res.content);
        this.totalElements.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải danh sách dự án');
      }
    });
  }

  onFilter(): void {
    this.page = 0;
    this.loadProjects();
  }

  resetFilter(): void {
    this.keyword = '';
    this.selectedStatus = '';
    this.myOnly = false;
    this.page = 0;
    this.loadProjects();
  }

  openCreateModal(): void {
    this.isEditMode.set(false);
    this.editingProjectId = null;
    this.editingVersion = 0;
    this.formError.set(null);
    this.projectForm.reset({
      code: '',
      name: '',
      customerName: '',
      projectManagerId: '',
      startDate: '',
      endDate: '',
      description: '',
      status: 'PLANNING'
    });
    this.showModal.set(true);
  }

  openEditModal(project: ProjectResponse): void {
    this.isEditMode.set(true);
    this.editingProjectId = project.id;
    this.editingVersion = project.version;
    this.formError.set(null);
    this.projectForm.patchValue({
      code: project.code,
      name: project.name,
      customerName: project.customerName || '',
      projectManagerId: project.projectManagerId || '',
      startDate: project.startDate || '',
      endDate: project.endDate || '',
      description: project.description || '',
      status: project.status
    });
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  saveProject(): void {
    if (this.projectForm.invalid || this.formSubmitting()) {
      this.projectForm.markAllAsTouched();
      return;
    }

    const formVal = this.projectForm.value;
    if (formVal.startDate && formVal.endDate && formVal.endDate < formVal.startDate) {
      this.formError.set('Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu');
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);

    if (this.isEditMode() && this.editingProjectId) {
      const updateReq = {
        ...formVal,
        version: this.editingVersion
      };
      this.projectService.updateProject(this.editingProjectId, updateReq).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadProjects();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Cập nhật dự án thất bại');
        }
      });
    } else {
      this.projectService.createProject(formVal).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.closeModal();
          this.loadProjects();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Tạo dự án mới thất bại');
        }
      });
    }
  }

  openDeleteModal(project: ProjectResponse): void {
    this.deletingProject = project;
    this.needsDeleteConfirm.set(false);
    this.deleteMessage.set(`Bạn có chắc chắn muốn xóa dự án "${project.name}" (${project.code})?`);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deletingProject = null;
  }

  confirmDelete(): void {
    if (!this.deletingProject) return;

    this.projectService.deleteProject(this.deletingProject.id, this.needsDeleteConfirm()).subscribe({
      next: () => {
        this.closeDeleteModal();
        this.loadProjects();
      },
      error: (err) => {
        if (err.status === 400 && !this.needsDeleteConfirm()) {
          this.needsDeleteConfirm.set(true);
          this.deleteMessage.set(err.message + '. Bạn có xác nhận xóa kèm tất cả các công việc liên quan?');
        } else {
          alert(err.message || 'Xóa dự án thất bại');
        }
      }
    });
  }
}
