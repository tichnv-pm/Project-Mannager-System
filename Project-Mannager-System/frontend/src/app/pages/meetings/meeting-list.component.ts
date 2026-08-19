import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { MeetingService } from './meeting.service';
import { ActionItemResponse, MeetingAttachment, MeetingResponse, UserBriefRef } from './meeting.model';
import { ProjectService } from '../projects/project.service';
import { ProjectOption } from '../dashboard/dashboard.model';
import { UserBrief } from '../../core/models/auth.model';

type QuickFilter = 'all' | 'today' | 'upcoming' | 'completed';

@Component({
  selector: 'app-meeting-list',
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
  templateUrl: './meeting-list.component.html',
  styleUrls: ['./meeting-list.component.scss']
})
export class MeetingListComponent implements OnInit, OnDestroy {
  private meetingService = inject(MeetingService);
  private projectService = inject(ProjectService);
  private fb = inject(FormBuilder);
  private destroy$ = new Subject<void>();

  // ─── UI State ─────────────────────────────────────────────────
  loading = signal(true);
  error = signal<string | null>(null);
  quickFilter = signal<QuickFilter>('all');

  // ─── Data ─────────────────────────────────────────────────────
  meetings = signal<MeetingResponse[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  projectsList = signal<ProjectOption[]>([]);
  usersList = signal<UserBrief[]>([]);

  // ─── Filter ───────────────────────────────────────────────────
  keyword = '';
  selectedProjectId = '';
  selectedStatus = '';
  fromTime = '';
  toTime = '';
  page = 0;
  size = 12;

  // ─── Modal: Create/Edit ────────────────────────────────────────
  showModal = signal(false);
  isEditMode = signal(false);
  editingMeetingId: string | null = null;
  editingVersion = 0;
  formError = signal<string | null>(null);
  formSubmitting = signal(false);
  meetingForm!: FormGroup;

  // ─── Modal: Complete (biên bản) ────────────────────────────────
  showCompleteModal = signal(false);
  targetMeeting: MeetingResponse | null = null;
  completeContent = '';
  completeConclusion = '';
  completeError = signal<string | null>(null);
  completeSubmitting = signal(false);

  // ─── Modal: Delete ────────────────────────────────────────────
  showDeleteModal = signal(false);
  deletingMeeting: MeetingResponse | null = null;
  deleteSubmitting = signal(false);

  // ─── Action Items ─────────────────────────────────────────────
  expandedActionItems = new Set<string>();
  actionItemsMap = new Map<string, ActionItemResponse[]>();
  actionItemsLoading = new Set<string>();

  // ─── Detail panel (participants + attachments) ─────────────────
  expandedDetails = new Set<string>();
  participantsMap = new Map<string, UserBriefRef[]>();
  participantsLoading = new Set<string>();
  attachmentsMap = new Map<string, MeetingAttachment[]>();
  attachmentsLoading = new Set<string>();
  uploadInProgress = signal(false);

  // ─── Modal: Action Item (create/edit) ──────────────────────────
  showAIModal = signal(false);
  isEditAIMode = signal(false);
  editingActionItem: ActionItemResponse | null = null;
  aiModalMeeting: MeetingResponse | null = null;
  aiFormError = signal<string | null>(null);
  aiSubmitting = signal(false);
  aiForm!: FormGroup;

  // ─── Modal: Convert Action Item → Task ─────────────────────────
  showConvertModal = signal(false);
  convertTarget: ActionItemResponse | null = null;
  convertDueDate = '';
  convertPriority = 'MEDIUM';
  convertError = signal<string | null>(null);
  convertSubmitting = signal(false);

  // ─── Keyword debounce ─────────────────────────────────────────
  private keywordSubject = new Subject<string>();

  constructor() {
    this.meetingForm = this.fb.group({
      projectId:     ['', [Validators.required]],
      title:         ['', [Validators.required, Validators.maxLength(200)]],
      chairpersonId: ['', [Validators.required]],
      startTime:     ['', [Validators.required]],
      endTime:       ['', [Validators.required]],
      location:      [''],
      meetingLink:   [''],
      agenda:        [''],
    });
    this.aiForm = this.fb.group({
      title:      ['', [Validators.required, Validators.maxLength(200)]],
      assigneeId: ['', [Validators.required]],
      dueDate:    [''],
      priority:   ['MEDIUM'],
      description:[''],
    });
  }

  ngOnInit(): void {
    this.loadOptions();
    this.loadMeetings();
    this.keywordSubject.pipe(debounceTime(400), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(() => { this.page = 0; this.loadMeetings(); });
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  // ─── Load ──────────────────────────────────────────────────────

  loadOptions(): void {
    this.projectService.getProjectsOptions().subscribe(p => this.projectsList.set(p));
    this.projectService.getUsersList().subscribe(u => this.usersList.set(u));
  }

  loadMeetings(): void {
    this.loading.set(true);
    this.error.set(null);
    const qf = this.quickFilter();

    if (qf === 'today') {
      this.meetingService.getTodayMeetings().subscribe({
        next: list => { this.meetings.set(list); this.totalElements.set(list.length); this.totalPages.set(1); this.loading.set(false); },
        error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải cuộc họp hôm nay'); }
      });
      return;
    }

    const params: any = {
      keyword: this.keyword || undefined,
      projectId: this.selectedProjectId || undefined,
      status: qf === 'upcoming' ? 'SCHEDULED' : qf === 'completed' ? 'COMPLETED' : (this.selectedStatus || undefined),
      fromTime: this.fromTime ? `${this.fromTime}:00` : undefined,
      toTime: this.toTime ? `${this.toTime}:59` : undefined,
      page: this.page,
      size: this.size,
    };

    this.meetingService.getMeetings(params).subscribe({
      next: res => {
        this.meetings.set(res.content || []);
        this.totalElements.set(res.totalElements || 0);
        this.totalPages.set(res.totalPages || 0);
        this.loading.set(false);
      },
      error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải danh sách cuộc họp'); }
    });
  }

  setQuickFilter(f: QuickFilter): void { this.quickFilter.set(f); this.page = 0; this.loadMeetings(); }
  onKeywordChange(): void { this.keywordSubject.next(this.keyword); }
  onFilter(): void { this.quickFilter.set('all'); this.page = 0; this.loadMeetings(); }

  resetFilter(): void {
    this.keyword = '';
    this.selectedProjectId = '';
    this.selectedStatus = '';
    this.fromTime = '';
    this.toTime = '';
    this.quickFilter.set('all');
    this.page = 0;
    this.loadMeetings();
  }

  hasActiveFilters(): boolean {
    return !!(this.keyword || this.selectedProjectId || this.selectedStatus || this.fromTime || this.toTime);
  }

  get pages(): number[] { return Array.from({ length: this.totalPages() }, (_, i) => i); }

  goToPage(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.page = p;
    this.loadMeetings();
  }

  // ─── Create/Edit Modal ─────────────────────────────────────────

  openCreateModal(): void {
    this.isEditMode.set(false);
    this.editingMeetingId = null;
    this.formError.set(null);
    this.meetingForm.reset({
      projectId: this.projectsList().length > 0 ? this.projectsList()[0].id : '',
      title: '', chairpersonId: '', startTime: '', endTime: '',
      location: '', meetingLink: '', agenda: '',
    });
    this.showModal.set(true);
  }

  openEditModal(m: MeetingResponse): void {
    this.isEditMode.set(true);
    this.editingMeetingId = m.id;
    this.editingVersion = m.version;
    this.formError.set(null);
    this.meetingForm.patchValue({
      projectId:     m.projectId,
      title:         m.title,
      chairpersonId: m.chairperson?.id || '',
      startTime:     m.startTime ? m.startTime.slice(0, 16) : '',
      endTime:       m.endTime ? m.endTime.slice(0, 16) : '',
      location:      m.location || '',
      meetingLink:   m.meetingLink || '',
      agenda:        m.agenda || '',
    });
    this.showModal.set(true);
  }

  closeModal(): void { this.showModal.set(false); }

  saveMeeting(): void {
    if (this.meetingForm.invalid || this.formSubmitting()) {
      this.meetingForm.markAllAsTouched();
      return;
    }
    this.formSubmitting.set(true);
    this.formError.set(null);
    const v = this.meetingForm.value;

    if (this.isEditMode() && this.editingMeetingId) {
      this.meetingService.updateMeeting(this.editingMeetingId, {
        title: v.title, chairpersonId: v.chairpersonId,
        startTime: new Date(v.startTime).toISOString(),
        endTime: new Date(v.endTime).toISOString(),
        location: v.location || undefined,
        meetingLink: v.meetingLink || undefined,
        agenda: v.agenda || undefined,
        version: this.editingVersion,
      }).subscribe({
        next: () => { this.formSubmitting.set(false); this.closeModal(); this.loadMeetings(); },
        error: err => { this.formSubmitting.set(false); this.formError.set(err?.error?.message || 'Cập nhật thất bại'); }
      });
    } else {
      this.meetingService.createMeeting({
        projectId: v.projectId, title: v.title, chairpersonId: v.chairpersonId,
        startTime: new Date(v.startTime).toISOString(),
        endTime: new Date(v.endTime).toISOString(),
        location: v.location || undefined,
        meetingLink: v.meetingLink || undefined,
        agenda: v.agenda || undefined,
      }).subscribe({
        next: () => { this.formSubmitting.set(false); this.closeModal(); this.loadMeetings(); },
        error: err => { this.formSubmitting.set(false); this.formError.set(err?.error?.message || 'Tạo cuộc họp thất bại'); }
      });
    }
  }

  // ─── Complete / Biên bản Modal ────────────────────────────────

  openCompleteModal(m: MeetingResponse): void {
    this.targetMeeting = m;
    this.completeContent = m.content || '';
    this.completeConclusion = m.conclusion || '';
    this.completeError.set(null);
    this.showCompleteModal.set(true);
  }

  closeCompleteModal(): void { this.showCompleteModal.set(false); this.targetMeeting = null; }

  submitComplete(): void {
    if (!this.targetMeeting || this.completeSubmitting()) return;
    if (!this.completeConclusion.trim()) {
      this.completeError.set('Kết luận cuộc họp (Conclusion) là bắt buộc');
      return;
    }
    this.completeSubmitting.set(true);
    this.meetingService.completeMeeting(this.targetMeeting.id, {
      content: this.completeContent || undefined,
      conclusion: this.completeConclusion,
    }).subscribe({
      next: () => { this.completeSubmitting.set(false); this.closeCompleteModal(); this.loadMeetings(); },
      error: err => { this.completeSubmitting.set(false); this.completeError.set(err?.error?.message || 'Hoàn thành họp thất bại'); }
    });
  }

  // ─── Delete Modal ─────────────────────────────────────────────

  openDeleteModal(m: MeetingResponse): void { this.deletingMeeting = m; this.showDeleteModal.set(true); }
  closeDeleteModal(): void { this.showDeleteModal.set(false); this.deletingMeeting = null; }

  confirmDelete(): void {
    if (!this.deletingMeeting || this.deleteSubmitting()) return;
    this.deleteSubmitting.set(true);
    this.meetingService.deleteMeeting(this.deletingMeeting.id).subscribe({
      next: () => { this.deleteSubmitting.set(false); this.closeDeleteModal(); this.loadMeetings(); },
      error: err => { this.deleteSubmitting.set(false); alert(err?.error?.message || 'Xóa cuộc họp thất bại'); }
    });
  }

  // ─── Action Items (inline expand) ─────────────────────────────

  toggleActionItems(meetingId: string): void {
    if (this.expandedActionItems.has(meetingId)) {
      this.expandedActionItems.delete(meetingId);
    } else {
      this.expandedActionItems.add(meetingId);
      if (!this.actionItemsMap.has(meetingId)) {
        this.loadActionItems(meetingId);
      }
    }
  }

  loadActionItems(meetingId: string): void {
    this.actionItemsLoading.add(meetingId);
    this.meetingService.getActionItems({ meetingId, size: 50 }).subscribe({
      next: res => {
        this.actionItemsMap.set(meetingId, res.content || []);
        this.actionItemsLoading.delete(meetingId);
      },
      error: () => this.actionItemsLoading.delete(meetingId)
    });
  }

  getActionItems(meetingId: string): ActionItemResponse[] {
    return this.actionItemsMap.get(meetingId) || [];
  }

  isActionItemsExpanded(meetingId: string): boolean {
    return this.expandedActionItems.has(meetingId);
  }

  isActionItemsLoading(meetingId: string): boolean {
    return this.actionItemsLoading.has(meetingId);
  }

  // ─── Detail Panel: Participants ───────────────────────────────

  toggleDetails(meetingId: string): void {
    if (this.expandedDetails.has(meetingId)) {
      this.expandedDetails.delete(meetingId);
      return;
    }
    this.expandedDetails.add(meetingId);
    if (!this.participantsMap.has(meetingId)) {
      this.loadParticipants(meetingId);
    }
    if (!this.attachmentsMap.has(meetingId)) {
      this.loadAttachments(meetingId);
    }
  }

  isDetailsExpanded(meetingId: string): boolean {
    return this.expandedDetails.has(meetingId);
  }

  loadParticipants(meetingId: string): void {
    this.participantsLoading.add(meetingId);
    this.meetingService.getMeeting(meetingId).subscribe({
      next: m => {
        this.participantsMap.set(meetingId, m.participants || []);
        this.participantsLoading.delete(meetingId);
      },
      error: () => this.participantsLoading.delete(meetingId)
    });
  }

  getParticipants(meetingId: string): UserBriefRef[] {
    return this.participantsMap.get(meetingId) || [];
  }

  isParticipantsLoading(meetingId: string): boolean {
    return this.participantsLoading.has(meetingId);
  }

  canAddParticipant(meetingId: string, userId: string): boolean {
    if (!userId) return false;
    return !this.getParticipants(meetingId).some(p => p.id === userId);
  }

  addParticipant(meetingId: string, userId: string): void {
    if (!userId || !this.canAddParticipant(meetingId, userId)) return;
    this.meetingService.updateParticipants(meetingId, [userId], []).subscribe({
      next: m => {
        this.participantsMap.set(meetingId, m.participants || []);
        this.refreshMeetingInList(m);
        this.formError.set(null);
      },
      error: err => this.formError.set(err?.error?.message || 'Thêm người tham gia thất bại')
    });
  }

  removeParticipant(meetingId: string, userId: string): void {
    this.meetingService.updateParticipants(meetingId, [], [userId]).subscribe({
      next: m => {
        this.participantsMap.set(meetingId, m.participants || []);
        this.refreshMeetingInList(m);
      },
      error: err => this.formError.set(err?.error?.message || 'Xóa người tham gia thất bại')
    });
  }

  // ─── Detail Panel: Attachments ────────────────────────────────

  loadAttachments(meetingId: string): void {
    this.attachmentsLoading.add(meetingId);
    this.meetingService.getAttachments(meetingId).subscribe({
      next: list => {
        this.attachmentsMap.set(meetingId, list);
        this.attachmentsLoading.delete(meetingId);
      },
      error: () => this.attachmentsLoading.delete(meetingId)
    });
  }

  getAttachments(meetingId: string): MeetingAttachment[] {
    return this.attachmentsMap.get(meetingId) || [];
  }

  isAttachmentsLoading(meetingId: string): boolean {
    return this.attachmentsLoading.has(meetingId);
  }

  uploadAttachment(meetingId: string, file: File | null): void {
    if (!file) return;
    this.uploadInProgress.set(true);
    this.formError.set(null);
    this.meetingService.uploadAttachment(meetingId, file).subscribe({
      next: () => {
        this.uploadInProgress.set(false);
        this.loadAttachments(meetingId);
      },
      error: err => {
        this.uploadInProgress.set(false);
        this.formError.set(err?.error?.message || 'Upload file thất bại');
      }
    });
  }

  deleteAttachment(meetingId: string, attachmentId: string): void {
    if (!confirm('Xóa file đính kèm này?')) return;
    this.meetingService.deleteAttachment(meetingId, attachmentId).subscribe({
      next: () => this.loadAttachments(meetingId),
      error: err => this.formError.set(err?.error?.message || 'Xóa file thất bại')
    });
  }

  downloadAttachment(meetingId: string, attachmentId: string): void {
    window.open(`/api/v1/meetings/${meetingId}/attachments/${attachmentId}/download`, '_blank');
  }

  getFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  // ─── Action Item Modal (create/edit) ──────────────────────────

  openCreateAIModal(m: MeetingResponse): void {
    this.aiModalMeeting = m;
    this.isEditAIMode.set(false);
    this.editingActionItem = null;
    this.aiFormError.set(null);
    this.aiForm.reset({
      title: '', assigneeId: '', dueDate: '', priority: 'MEDIUM', description: ''
    });
    this.showAIModal.set(true);
  }

  openEditAIModal(m: MeetingResponse, ai: ActionItemResponse): void {
    this.aiModalMeeting = m;
    this.isEditAIMode.set(true);
    this.editingActionItem = ai;
    this.aiFormError.set(null);
    this.aiForm.patchValue({
      title: ai.title,
      assigneeId: ai.assignee?.id || '',
      dueDate: ai.dueDate || '',
      priority: ai.priority || 'MEDIUM',
      description: ai.description || '',
    });
    this.showAIModal.set(true);
  }

  closeAIModal(): void {
    this.showAIModal.set(false);
    this.aiModalMeeting = null;
    this.editingActionItem = null;
  }

  saveActionItem(): void {
    if (!this.aiModalMeeting || this.aiForm.invalid || this.aiSubmitting()) {
      this.aiForm.markAllAsTouched();
      return;
    }
    this.aiSubmitting.set(true);
    this.aiFormError.set(null);
    const v = this.aiForm.value;
    const meetingId = this.aiModalMeeting.id;
    const projectId = this.aiModalMeeting.projectId;

    if (this.isEditAIMode() && this.editingActionItem) {
      this.meetingService.updateActionItem(this.editingActionItem.id, {
        title: v.title,
        assigneeId: v.assigneeId,
        dueDate: v.dueDate || undefined,
        priority: v.priority,
        description: v.description || undefined,
        version: this.editingActionItem.version,
      }).subscribe({
        next: () => {
          this.aiSubmitting.set(false);
          this.closeAIModal();
          this.loadActionItems(meetingId);
        },
        error: err => { this.aiSubmitting.set(false); this.aiFormError.set(err?.error?.message || 'Cập nhật thất bại'); }
      });
    } else {
      this.meetingService.createActionItem({
        meetingId,
        projectId,
        title: v.title,
        assigneeId: v.assigneeId,
        dueDate: v.dueDate || undefined,
        priority: v.priority,
        description: v.description || undefined,
      }).subscribe({
        next: () => {
          this.aiSubmitting.set(false);
          this.closeAIModal();
          this.loadActionItems(meetingId);
        },
        error: err => { this.aiSubmitting.set(false); this.aiFormError.set(err?.error?.message || 'Tạo action item thất bại'); }
      });
    }
  }

  deleteActionItem(m: MeetingResponse, ai: ActionItemResponse): void {
    if (!confirm(`Xóa action item "${ai.title}"?`)) return;
    this.meetingService.deleteActionItem(ai.id).subscribe({
      next: () => this.loadActionItems(m.id),
      error: err => this.formError.set(err?.error?.message || 'Xóa action item thất bại')
    });
  }

  // ─── Convert Action Item → Task ───────────────────────────────

  openConvertModal(ai: ActionItemResponse): void {
    this.convertTarget = ai;
    this.convertDueDate = ai.dueDate || '';
    this.convertPriority = ai.priority || 'MEDIUM';
    this.convertError.set(null);
    this.showConvertModal.set(true);
  }

  closeConvertModal(): void {
    this.showConvertModal.set(false);
    this.convertTarget = null;
  }

  confirmConvert(): void {
    if (!this.convertTarget || this.convertSubmitting()) return;
    this.convertSubmitting.set(true);
    this.convertError.set(null);
    this.meetingService.convertActionItemToTask(this.convertTarget.id, {
      dueDate: this.convertDueDate || undefined,
      priority: this.convertPriority,
    }).subscribe({
      next: () => {
        this.convertSubmitting.set(false);
        this.closeConvertModal();
        if (this.convertTarget) {
          const meetingId = this.convertTarget.meetingId;
          this.loadActionItems(meetingId);
          this.refreshMeetingById(meetingId);
        }
        alert('✅ Đã chuyển Action Item thành Task');
      },
      error: err => {
        this.convertSubmitting.set(false);
        this.convertError.set(err?.error?.message || 'Chuyển thành task thất bại');
      }
    });
  }

  private refreshMeetingInList(m: MeetingResponse): void {
    this.meetings.update(list => list.map(item => item.id === m.id ? m : item));
  }

  private refreshMeetingById(meetingId: string): void {
    this.meetingService.getMeeting(meetingId).subscribe({
      next: m => this.meetings.update(list => list.map(item => item.id === m.id ? m : item)),
      error: () => undefined
    });
  }

  // ─── Helpers ──────────────────────────────────────────────────

  getDurationLabel(startTime: string, endTime: string): string {    if (!startTime || !endTime) return '';
    const diffMs = new Date(endTime).getTime() - new Date(startTime).getTime();
    const mins = Math.round(diffMs / 60000);
    if (mins < 60) return `${mins} phút`;
    return `${Math.floor(mins / 60)}h${mins % 60 > 0 ? (mins % 60) + 'm' : ''}`;
  }

  getStatusIcon(status: string): string {
    const m: Record<string, string> = {
      'SCHEDULED': '📅', 'IN_PROGRESS': '▶️', 'COMPLETED': '✅', 'CANCELLED': '❌'
    };
    return m[status] || '📅';
  }

  isToday(dateStr: string): boolean {
    const d = new Date(dateStr);
    const n = new Date();
    return d.getFullYear() === n.getFullYear() && d.getMonth() === n.getMonth() && d.getDate() === n.getDate();
  }

  isPast(dateStr: string): boolean {
    return new Date(dateStr) < new Date();
  }

  getActionItemStatusColor(status: string): string {
    const m: Record<string, string> = {
      'OPEN': '#64748b', 'IN_PROGRESS': '#3b82f6', 'DONE': '#22c55e', 'CANCELLED': '#475569'
    };
    return m[status] || '#64748b';
  }
}
