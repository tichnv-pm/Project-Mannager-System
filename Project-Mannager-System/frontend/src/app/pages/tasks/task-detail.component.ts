import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { UserBrief } from '../../core/models/auth.model';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { PriorityChipComponent } from '../../shared/components/priority-chip.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProjectService } from '../projects/project.service';
import { TaskService } from './task.service';
import { GitService } from './git.service';
import { GitCommitResponse, GitPullRequestResponse } from './git.model';
import {
  TaskAttachment,
  TaskComment,
  TaskDetailResponse,
  TaskHistoryEntry,
  TaskStatus,
  TaskSummaryResponse
} from './task.model';

type DetailTab = 'overview' | 'comments' | 'attachments' | 'history' | 'children' | 'git';

@Component({
  selector: 'app-task-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    StatusChipComponent,
    PriorityChipComponent,
    HasPermissionDirective
  ],
  templateUrl: './task-detail.component.html',
  styleUrls: ['./task-detail.component.scss']
})
export class TaskDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private taskService = inject(TaskService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);

  taskId: string | null = null;
  usersList = signal<UserBrief[]>([]);
  projectMembers = signal<ProjectMemberResponse[]>([]);

  // ─── Core Data ────────────────────────────────────────────────
  task = signal<TaskDetailResponse | null>(null);
  comments = signal<TaskComment[]>([]);
  attachments = signal<TaskAttachment[]>([]);
  history = signal<TaskHistoryEntry[]>([]);
  childTasks = signal<TaskSummaryResponse[]>([]);

  // ─── Loading States ───────────────────────────────────────────
  loading = signal(true);
  error = signal<string | null>(null);
  commentsLoading = signal(false);
  attachmentsLoading = signal(false);
  historyLoading = signal(false);
  childrenLoading = signal(false);
  gitLoading = signal(false);

  // ─── Tab ─────────────────────────────────────────────────────
  activeTab = signal<DetailTab>('overview');

  // ─── Git Info ────────────────────────────────────────────────
  private gitService = inject(GitService);
  gitCommits = signal<GitCommitResponse[]>([]);
  gitPrs = signal<GitPullRequestResponse[]>([]);

  // ─── Progress Update ──────────────────────────────────────────
  newProgress = signal(0);
  progressUpdating = signal(false);

  // ─── Status Change ────────────────────────────────────────────
  showStatusModal = signal(false);
  newStatus: TaskStatus = 'IN_PROGRESS';
  blockerReason = '';
  statusError = signal<string | null>(null);
  statusSubmitting = signal(false);

  // ─── Edit Modal ───────────────────────────────────────────────
  showEditModal = signal(false);
  editForm!: FormGroup;
  editSubmitting = signal(false);
  editError = signal<string | null>(null);

  // ─── Comments ─────────────────────────────────────────────────
  newCommentContent = '';
  submittingComment = signal(false);
  editingCommentId: string | null = null;
  editingCommentContent = '';
  updatingComment = signal(false);

  // ─── Attachments ──────────────────────────────────────────────
  uploadingFile = signal(false);
  uploadError = signal<string | null>(null);
  selectedFile: File | null = null;

  // ─── Delete Task ──────────────────────────────────────────────
  showDeleteModal = signal(false);
  deleteSubmitting = signal(false);

  constructor() {
    this.editForm = this.fb.group({
      title:          ['', [Validators.required, Validators.maxLength(200)]],
      type:           ['TASK'],
      priority:       ['MEDIUM'],
      assigneeId:     [''],
      startDate:      [''],
      dueDate:        [''],
      description:    [''],
      notes:          [''],
      estimateMinutes: [null],
    });
  }

  ngOnInit(): void {
    this.taskId = this.route.snapshot.paramMap.get('id');
    if (this.taskId) {
      this.loadTaskDetail();
    }
    this.projectService.getUsersList().subscribe(users => this.usersList.set(users));
  }

  // ─── Load Methods ────────────────────────────────────────────

  loadTaskDetail(): void {
    if (!this.taskId) return;
    this.loading.set(true);
    this.taskService.getTask(this.taskId).subscribe({
      next: (detail) => {
        this.task.set(detail);
        this.newProgress.set(detail.progress);
        this.loading.set(false);
        if (detail.projectId) {
          this.projectService.getMembers(detail.projectId).subscribe({
            next: members => this.projectMembers.set(members || []),
            error: () => this.projectMembers.set([])
          });
        }
        // Auto-load tab data
        this.loadComments();
        this.loadAttachments();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.extractError(err, 'Không thể tải chi tiết công việc'));
      }
    });
  }

  getRoleLabel(role: string): string {
    const map: Record<string, string> = {
      'PROJECT_MANAGER': 'PM',
      'TECH_LEAD': 'Tech Lead',
      'DEVELOPER': 'Developer',
      'DEV': 'Developer',
      'TESTER': 'Tester',
      'BUSINESS_ANALYST': 'BA',
      'BA': 'BA',
      'DEVOPS': 'DevOps',
      'MEMBER': 'Thành viên',
      'PROJECT_MEMBER': 'Thành viên'
    };
    return map[role] || role;
  }

  private extractError(err: any, fallback: string): string {
    if (!err) return fallback;
    if (err.fieldErrors && Array.isArray(err.fieldErrors) && err.fieldErrors.length > 0) {
      return err.fieldErrors.map((f: any) => f.message).join('. ');
    }
    if (err.message) return err.message;
    if (err.error?.fieldErrors && Array.isArray(err.error.fieldErrors) && err.error.fieldErrors.length > 0) {
      return err.error.fieldErrors.map((f: any) => f.message).join('. ');
    }
    if (err.error?.message) return err.error.message;
    return fallback;
  }

  loadComments(): void {
    if (!this.taskId) return;
    this.commentsLoading.set(true);
    this.taskService.getComments(this.taskId).subscribe({
      next: (c) => { this.comments.set(c); this.commentsLoading.set(false); },
      error: () => this.commentsLoading.set(false)
    });
  }

  loadAttachments(): void {
    if (!this.taskId) return;
    this.attachmentsLoading.set(true);
    this.taskService.getAttachments(this.taskId).subscribe({
      next: (a) => { this.attachments.set(a); this.attachmentsLoading.set(false); },
      error: () => this.attachmentsLoading.set(false)
    });
  }

  loadHistory(): void {
    if (!this.taskId || this.history().length > 0) return;
    this.historyLoading.set(true);
    this.taskService.getHistory(this.taskId).subscribe({
      next: (h) => { this.history.set(h); this.historyLoading.set(false); },
      error: () => this.historyLoading.set(false)
    });
  }

  loadChildren(): void {
    if (!this.taskId || this.childTasks().length > 0) return;
    this.childrenLoading.set(true);
    this.taskService.getChildTasks(this.taskId).subscribe({
      next: (c) => { this.childTasks.set(c); this.childrenLoading.set(false); },
      error: () => this.childrenLoading.set(false)
    });
  }

  setTab(tab: DetailTab): void {
    this.activeTab.set(tab);
    if (tab === 'history') this.loadHistory();
    if (tab === 'children') this.loadChildren();
    if (tab === 'git') this.loadGitInfo();
  }

  loadGitInfo(): void {
    if (!this.taskId) return;
    this.gitLoading.set(true);
    this.gitService.getGitInfo(this.taskId).subscribe({
      next: (res) => {
        this.gitCommits.set(res.commits || []);
        this.gitPrs.set(res.pullRequests || []);
        this.gitLoading.set(false);
      },
      error: () => this.gitLoading.set(false)
    });
  }

  // ─── Progress Update ─────────────────────────────────────────

  updateProgress(): void {
    if (!this.taskId || this.progressUpdating()) return;
    this.progressUpdating.set(true);
    this.taskService.updateProgress(this.taskId, { progress: this.newProgress() }).subscribe({
      next: (updated) => {
        this.task.set(updated);
        this.newProgress.set(updated.progress);
        this.progressUpdating.set(false);
      },
      error: (err) => {
        this.progressUpdating.set(false);
        alert(err?.error?.message || 'Cập nhật tiến độ thất bại');
      }
    });
  }

  getProgressColor(progress: number): string {
    if (progress >= 100) return '#22c55e';
    if (progress >= 60)  return '#3b82f6';
    if (progress >= 30)  return '#f59e0b';
    return '#ef4444';
  }

  // ─── Status Change ───────────────────────────────────────────

  allowedStatuses = signal<{ value: TaskStatus; label: string }[]>([]);

  openStatusModal(): void {
    const current = this.task()?.status ?? 'TODO';
    const statuses = this.getAllowedStatuses(current);
    this.allowedStatuses.set(statuses);
    this.newStatus = statuses[0]?.value ?? 'IN_PROGRESS';
    this.blockerReason = this.task()?.blockerReason || '';
    this.statusError.set(null);
    this.showStatusModal.set(true);
  }

  getAllowedStatuses(current: TaskStatus): { value: TaskStatus; label: string }[] {
    switch (current) {
      case 'TODO':
        return [
          { value: 'IN_PROGRESS', label: '🔄 Đang làm (IN_PROGRESS)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      case 'IN_PROGRESS':
        return [
          { value: 'REVIEW', label: '🔍 Đang review (REVIEW)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      case 'REVIEW':
        return [
          { value: 'DONE', label: '✅ Hoàn thành (DONE)' },
          { value: 'IN_PROGRESS', label: '🔄 Cần sửa - Về Đang làm (IN_PROGRESS)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      case 'BLOCKED':
        return [
          { value: 'IN_PROGRESS', label: '🔄 Tiếp tục làm (IN_PROGRESS)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
      default:
        return [
          { value: 'TODO', label: '📌 Cần làm (TODO)' },
          { value: 'IN_PROGRESS', label: '🔄 Đang làm (IN_PROGRESS)' },
          { value: 'REVIEW', label: '🔍 Đang review (REVIEW)' },
          { value: 'DONE', label: '✅ Hoàn thành (DONE)' },
          { value: 'BLOCKED', label: '🚧 Tắc nghẽn (BLOCKED)' },
          { value: 'CANCELLED', label: '❌ Đã hủy (CANCELLED)' }
        ];
    }
  }

  closeStatusModal(): void { this.showStatusModal.set(false); }

  confirmStatusChange(): void {
    if (!this.taskId || this.statusSubmitting()) return;
    if (this.newStatus === 'BLOCKED' && !this.blockerReason.trim()) {
      this.statusError.set('Vui lòng nhập lý do bị tắc nghẽn');
      return;
    }
    this.statusSubmitting.set(true);
    this.taskService.updateStatus(this.taskId, {
      status: this.newStatus,
      blockerReason: this.newStatus === 'BLOCKED' ? this.blockerReason : undefined
    }).subscribe({
      next: (updated) => {
        this.task.set(updated);
        this.statusSubmitting.set(false);
        this.closeStatusModal();
        this.history.set([]);
      },
      error: (err) => {
        this.statusSubmitting.set(false);
        const msg = err?.error?.message || err?.message || 'Chuyển trạng thái thất bại';
        this.statusError.set(msg);
      }
    });
  }

  // ─── Edit Modal ──────────────────────────────────────────────

  openEditModal(): void {
    const t = this.task();
    if (!t) return;
    this.editError.set(null);
    this.editForm.patchValue({
      title: t.title, type: t.type, priority: t.priority,
      assigneeId: t.assignee?.id || '',
      startDate: t.startDate || '', dueDate: t.dueDate || '',
      description: t.description || '', notes: t.notes || '',
      estimateMinutes: t.estimateMinutes ?? null,
    });
    this.showEditModal.set(true);
  }

  closeEditModal(): void { this.showEditModal.set(false); }

  saveEdit(): void {
    if (!this.taskId || this.editForm.invalid || this.editSubmitting()) {
      this.editForm.markAllAsTouched();
      return;
    }
    const t = this.task();
    if (!t) return;
    this.editSubmitting.set(true);
    const v = this.editForm.value;
    this.taskService.updateTask(this.taskId, {
      title: v.title, type: v.type, priority: v.priority,
      assigneeId: v.assigneeId || null,
      startDate: v.startDate || null, dueDate: v.dueDate || null,
      description: v.description || null, notes: v.notes || null,
      estimateMinutes: v.estimateMinutes || null,
      progress: t.progress, version: t.version,
      status: t.status,
      blocked: t.blocked,
      blockerReason: t.blockerReason || null,
    } as any).subscribe({
      next: (updated) => {
        this.task.set(updated);
        this.editSubmitting.set(false);
        this.closeEditModal();
      },
      error: (err) => {
        this.editSubmitting.set(false);
        this.editError.set(this.extractError(err, 'Cập nhật thất bại'));
      }
    });
  }

  // ─── Delete ──────────────────────────────────────────────────

  openDeleteModal(): void { this.showDeleteModal.set(true); }
  closeDeleteModal(): void { this.showDeleteModal.set(false); }

  confirmDelete(): void {
    if (!this.taskId || this.deleteSubmitting()) return;
    this.deleteSubmitting.set(true);
    this.taskService.deleteTask(this.taskId).subscribe({
      next: () => {
        this.deleteSubmitting.set(false);
        this.router.navigate(['/tasks']);
      },
      error: (err) => {
        this.deleteSubmitting.set(false);
        alert(err?.error?.message || 'Xóa công việc thất bại');
      }
    });
  }

  // ─── Comments ────────────────────────────────────────────────

  submitComment(): void {
    if (!this.taskId || !this.newCommentContent.trim() || this.submittingComment()) return;
    this.submittingComment.set(true);
    this.taskService.addComment(this.taskId, this.newCommentContent).subscribe({
      next: () => {
        this.newCommentContent = '';
        this.submittingComment.set(false);
        this.loadComments();
      },
      error: (err) => {
        this.submittingComment.set(false);
        alert(err?.error?.message || 'Gửi bình luận thất bại');
      }
    });
  }

  startEditComment(comment: TaskComment): void {
    this.editingCommentId = comment.id;
    this.editingCommentContent = comment.content;
  }

  cancelEditComment(): void {
    this.editingCommentId = null;
    this.editingCommentContent = '';
  }

  saveComment(): void {
    if (!this.taskId || !this.editingCommentId || !this.editingCommentContent.trim() || this.updatingComment()) return;
    this.updatingComment.set(true);
    this.taskService.updateComment(this.taskId, this.editingCommentId, this.editingCommentContent).subscribe({
      next: () => {
        this.cancelEditComment();
        this.updatingComment.set(false);
        this.loadComments();
      },
      error: (err) => {
        this.updatingComment.set(false);
        alert(err?.error?.message || 'Cập nhật bình luận thất bại');
      }
    });
  }

  deleteComment(commentId: string): void {
    if (!this.taskId || !confirm('Xóa bình luận này?')) return;
    this.taskService.deleteComment(this.taskId, commentId).subscribe({
      next: () => this.loadComments(),
      error: (err) => alert(err?.error?.message || 'Xóa bình luận thất bại')
    });
  }

  // ─── Attachments ─────────────────────────────────────────────

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      this.uploadError.set(null);
    }
  }

  uploadAttachment(): void {
    if (!this.taskId || !this.selectedFile || this.uploadingFile()) return;

    const maxSizeMB = 10;
    if (this.selectedFile.size > maxSizeMB * 1024 * 1024) {
      this.uploadError.set(`File không được vượt quá ${maxSizeMB}MB`);
      return;
    }

    this.uploadingFile.set(true);
    this.uploadError.set(null);
    this.taskService.uploadAttachment(this.taskId, this.selectedFile).subscribe({
      next: () => {
        this.selectedFile = null;
        this.uploadingFile.set(false);
        this.loadAttachments();
      },
      error: (err) => {
        this.uploadingFile.set(false);
        this.uploadError.set(err?.error?.message || 'Upload file thất bại');
      }
    });
  }

  deleteAttachment(attachmentId: string, fileName: string): void {
    if (!this.taskId || !confirm(`Xóa file "${fileName}"?`)) return;
    this.taskService.deleteAttachment(this.taskId, attachmentId).subscribe({
      next: () => this.loadAttachments(),
      error: (err) => alert(err?.error?.message || 'Xóa file thất bại')
    });
  }

  getFileIcon(contentType: string): string {
    if (contentType?.includes('image')) return '🖼️';
    if (contentType?.includes('pdf')) return '📕';
    if (contentType?.includes('excel') || contentType?.includes('spreadsheet')) return '📊';
    if (contentType?.includes('word') || contentType?.includes('document')) return '📝';
    if (contentType?.includes('zip') || contentType?.includes('rar')) return '🗜️';
    return '📄';
  }

  formatFileSize(bytes: number): string {
    if (!bytes) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  // ─── History ─────────────────────────────────────────────────

  getHistoryChanges(entry: TaskHistoryEntry): string[] {
    if (entry.changes) {
      return Object.entries(entry.changes).map(([field, change]) => {
        const c = change as any;
        return `${field}: "${c.from ?? '—'}" → "${c.to ?? '—'}"`;
      });
    }
    if (entry.fieldName) {
      return [`${entry.fieldName}: "${entry.oldValue ?? '—'}" → "${entry.newValue ?? '—'}"`];
    }
    return [];
  }

  getHistoryUser(entry: TaskHistoryEntry): string {
    return entry.changedByFullName || entry.changedByUsername || entry.userFullName || entry.changedBy || '—';
  }

  getHistoryTime(entry: TaskHistoryEntry): string {
    return entry.changedAt || entry.createdAt || '';
  }

  // ─── Helpers ─────────────────────────────────────────────────

  isOverdue(): boolean {
    const t = this.task();
    if (!t?.dueDate || t.status === 'DONE' || t.status === 'CANCELLED') return false;
    return t.dueDate < new Date().toISOString().slice(0, 10);
  }

  getTypeLabel(type: string): string {
    const m: Record<string, string> = {
      'TASK': '📋 Công việc', 'FEATURE': '✨ Tính năng',
      'BUG': '🐛 Lỗi', 'IMPROVEMENT': '⬆️ Cải tiến', 'OTHER': '📦 Khác'
    };
    return m[type] || type;
  }

  getSourceLabel(source: string): string {
    const m: Record<string, string> = {
      'MANUAL': 'Thủ công', 'MEETING': 'Từ cuộc họp',
      'ACTION_ITEM': 'Action Item', 'ISSUE': 'Từ Issue', 'OTHER': 'Khác'
    };
    return m[source] || source;
  }
}
