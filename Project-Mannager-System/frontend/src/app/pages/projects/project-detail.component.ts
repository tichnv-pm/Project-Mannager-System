import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectService } from './project.service';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectMemberResponse, ProjectMemberRole, ProjectResponse } from './project.model';
import { UserBrief } from '../../core/models/auth.model';
import { ProjectWikiComponent } from './project-wiki.component';
import { ProjectSprintsComponent } from './sprints/project-sprints.component';
import { ProjectQaComponent } from './qa/project-qa.component';
import { ProjectFinanceComponent } from './finance/project-finance.component';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    PageHeaderComponent,
    StatusChipComponent,
    HasPermissionDirective,
    ProjectWikiComponent,
    ProjectSprintsComponent,
    ProjectQaComponent,
    ProjectFinanceComponent
  ],
  templateUrl: './project-detail.component.html',
  styleUrls: ['./project-detail.component.scss']
})
export class ProjectDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private projectService = inject(ProjectService);
  authService = inject(AuthService);
  private fb = inject(FormBuilder);

  projectId: string | null = null;
  project = signal<ProjectResponse | null>(null);
  members = signal<ProjectMemberResponse[]>([]);
  usersList = signal<UserBrief[]>([]);

  loading = signal(true);
  error = signal<string | null>(null);

  activeTab: 'overview' | 'members' | 'wiki' | 'sprints' | 'qa' | 'finance' = 'overview';

  // Member Modal State
  showMemberModal = signal(false);
  memberFormError = signal<string | null>(null);
  memberFormSubmitting = signal(false);

  memberForm: FormGroup;

  constructor() {
    this.memberForm = this.fb.group({
      userId: ['', [Validators.required]],
      role: ['DEVELOPER', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id');
    if (this.projectId) {
      this.loadProjectDetail();
      this.loadMembers();
      this.loadUsers();
    }
  }

  loadProjectDetail(): void {
    if (!this.projectId) return;
    this.loading.set(true);

    this.projectService.getProject(this.projectId).subscribe({
      next: (prj) => {
        this.project.set(prj);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải thông tin dự án');
      }
    });
  }

  loadMembers(): void {
    if (!this.projectId) return;
    this.projectService.getMembers(this.projectId).subscribe(members => {
      this.members.set(members);
    });
  }

  loadUsers(): void {
    this.projectService.getUsersList().subscribe(users => {
      this.usersList.set(users);
    });
  }

  openAddMemberModal(): void {
    this.memberFormError.set(null);
    this.memberForm.reset({
      userId: '',
      role: 'DEV'
    });
    this.showMemberModal.set(true);
  }

  closeMemberModal(): void {
    this.showMemberModal.set(false);
  }

  addMember(): void {
    if (this.memberForm.invalid || !this.projectId || this.memberFormSubmitting()) {
      this.memberForm.markAllAsTouched();
      return;
    }

    this.memberFormSubmitting.set(true);
    this.memberFormError.set(null);

    this.projectService.addMember(this.projectId, this.memberForm.value).subscribe({
      next: () => {
        this.memberFormSubmitting.set(false);
        this.closeMemberModal();
        this.loadMembers();
        this.loadProjectDetail();
      },
      error: (err) => {
        this.memberFormSubmitting.set(false);
        this.memberFormError.set(err.message || 'Thêm thành viên thất bại');
      }
    });
  }

  onChangeRole(member: ProjectMemberResponse, event: Event): void {
    if (!this.projectId) return;
    const newRole = (event.target as HTMLSelectElement).value as ProjectMemberRole;

    this.projectService.updateMemberRole(this.projectId, member.userId, newRole).subscribe({
      next: () => {
        this.loadMembers();
      },
      error: (err) => {
        alert(err.message || 'Đổi vai trò thất bại');
        this.loadMembers();
      }
    });
  }

  removeMember(member: ProjectMemberResponse): void {
    if (!this.projectId) return;
    if (!confirm(`Bạn có chắc muốn xóa thành viên "${member.fullName || member.username}" khỏi dự án?`)) {
      return;
    }

    this.projectService.removeMember(this.projectId, member.userId).subscribe({
      next: () => {
        this.loadMembers();
        this.loadProjectDetail();
      },
      error: (err) => {
        alert(err.message || 'Xóa thành viên thất bại');
      }
    });
  }

  getRoleLabel(role: string): string {
    const map: Record<string, string> = {
      'PROJECT_MANAGER': 'Quản lý dự án (PM)',
      'TECH_LEAD': 'Trưởng nhóm kỹ thuật (Tech Lead)',
      'DEVELOPER': 'Lập trình viên (Developer)',
      'DEV': 'Lập trình viên (Developer)',
      'TESTER': 'Kiểm thử viên (Tester)',
      'BUSINESS_ANALYST': 'Phân tích nghiệp vụ (BA)',
      'BA': 'Phân tích nghiệp vụ (BA)',
      'DEVOPS': 'Kỹ sư DevOps',
      'MEMBER': 'Thành viên khác',
      'PROJECT_MEMBER': 'Thành viên khác'
    };
    return map[role] || role;
  }
}
