import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectService } from '../projects/project.service';
import { ProjectOption } from '../dashboard/dashboard.model';
import { QualityKpiReport, ReportService, ReportShareLink, ReportType } from './report.service';

type ReportTabKey = 'tasks-by-status' | 'tasks-by-assignee' | 'overdue-tasks' | 'project-progress' | 'risk-issue-summary' | 'quality-kpi';

@Component({
  selector: 'app-report-list',
  standalone: true,
  imports: [CommonModule, FormsModule, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './report-list.component.html',
  styleUrls: ['./report-list.component.scss']
})
export class ReportListComponent implements OnInit, OnDestroy {
  readonly Math = Math;
  private reportService = inject(ReportService);
  private projectService = inject(ProjectService);
  private authService = inject(AuthService);
  private destroy$ = new Subject<void>();

  // ─── State ────────────────────────────────────────────────────
  activeTab = signal<ReportTabKey>('tasks-by-status');

  readonly reportTabs: { key: ReportTabKey; label: string }[] = [
    { key: 'tasks-by-status', label: '📊 Theo trạng thái' },
    { key: 'tasks-by-assignee', label: '👤 Theo người làm' },
    { key: 'overdue-tasks', label: '⏰ Trễ hạn' },
    { key: 'project-progress', label: '📈 Tiến độ dự án' },
    { key: 'risk-issue-summary', label: '⚠️ Risk & Issue' },
    { key: 'quality-kpi', label: '🎯 KPI chất lượng' }
  ];
  loading = signal(false);
  error = signal<string | null>(null);

  // ─── Filters ──────────────────────────────────────────────────
  projectsList = signal<ProjectOption[]>([]);
  selectedProjectId = '';
  fromDate = '';
  toDate = '';

  // ─── Data ─────────────────────────────────────────────────────
  statusReport = signal<{ status: string; count: number }[]>([]);
  assigneeReport = signal<{ assigneeId: string; fullName: string; count: number; doneCount: number }[]>([]);
  overdueTasks = signal<any[]>([]);
  overdueTotal = signal(0);
  overduePage = 0;
  overdueSize = 50;
  progressReport = signal<{ projectId: string; code: string; name: string; progress: number; totalTasks: number; doneTasks: number }[]>([]);
  riskIssueReport = signal<{ openRisks: number; openIssues: number; risksByLevel: { level: string; count: number }[]; issuesBySeverity: { severity: string; count: number }[] } | null>(null);
  qualityKpiReport = signal<QualityKpiReport | null>(null);

  exporting = signal(false);

  // ─── Share-link báo cáo tiến độ (GĐ4 cải tiến) ──────────────────
  shareLinks = signal<ReportShareLink[]>([]);
  shareLinksLoading = signal(false);
  shareLinksError = signal<string | null>(null);
  showShareForm = signal(false);
  newShareLabel = '';
  newShareExpiresAt = '';
  shareBusy = signal(false);
  copiedLinkId = signal<string | null>(null);

  readonly statusLabels: Record<string, string> = {
    TODO: 'Cần làm', IN_PROGRESS: 'Đang làm', REVIEW: 'Review', DONE: 'Hoàn thành', BLOCKED: 'Bị chặn', CANCELLED: 'Đã hủy'
  };
  readonly statusColors: Record<string, string> = {
    TODO: '#3b82f6', IN_PROGRESS: '#f59e0b', REVIEW: '#a855f7', DONE: '#22c55e', BLOCKED: '#ef4444', CANCELLED: '#64748b'
  };
  readonly levelLabels: Record<string, string> = {
    LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Nghiêm trọng'
  };
  readonly severityLabels: Record<string, string> = {
    LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Nghiêm trọng'
  };

  ngOnInit(): void {
    this.projectService.getProjectsOptions().subscribe(p => this.projectsList.set(p));
    this.loadReport();
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  get today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  switchTab(tab: ReportTabKey): void {
    this.activeTab.set(tab);
    this.overduePage = 0;
    this.error.set(null);
    this.loadReport();
  }

  levelColor(level: string): string {
    return this.statusColors[level] || '#64748b';
  }

  severityColor(severity: string): string {
    return this.statusColors[severity] || '#64748b';
  }

  hasFilters(): boolean {
    return !!(this.selectedProjectId || this.fromDate || this.toDate);
  }

  resetFilters(): void {
    this.selectedProjectId = '';
    this.fromDate = '';
    this.toDate = '';
    this.overduePage = 0;
    this.loadReport();
  }

  loadReport(): void {
    this.loading.set(true);
    this.error.set(null);
    const tab = this.activeTab();
    const params = {
      projectId: this.selectedProjectId || undefined,
      fromDate: this.fromDate || undefined,
      toDate: this.toDate || undefined,
      page: this.overduePage,
      size: this.overdueSize,
    };

    switch (tab) {
      case 'tasks-by-status':
        this.reportService.getTasksByStatus(params).pipe(takeUntil(this.destroy$)).subscribe({
          next: res => { this.statusReport.set(res.items || []); this.loading.set(false); },
          error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải báo cáo'); }
        });
        break;
      case 'tasks-by-assignee':
        this.reportService.getTasksByAssignee(params).pipe(takeUntil(this.destroy$)).subscribe({
          next: res => { this.assigneeReport.set(res.items || []); this.loading.set(false); },
          error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải báo cáo'); }
        });
        break;
      case 'overdue-tasks':
        this.reportService.getOverdueTasks(params).pipe(takeUntil(this.destroy$)).subscribe({
          next: res => { this.overdueTasks.set(res.content || []); this.overdueTotal.set(res.totalElements || 0); this.loading.set(false); },
          error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải báo cáo'); }
        });
        break;
      case 'project-progress': {
        const ids = this.selectedProjectId ? [this.selectedProjectId] : undefined;
        this.reportService.getProjectProgress(ids).pipe(takeUntil(this.destroy$)).subscribe({
          next: res => { this.progressReport.set(res.items || []); this.loading.set(false); },
          error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải báo cáo'); }
        });
        this.shareLinks.set([]);
        if (this.selectedProjectId && this.canShare()) {
          this.loadShareLinks();
        }
        break;
      }
      case 'risk-issue-summary':
        if (!this.selectedProjectId) {
          this.error.set('Vui lòng chọn dự án để xem tổng hợp Risk & Issue');
          this.loading.set(false);
          break;
        }
        this.reportService.getRiskIssueSummary(params).pipe(takeUntil(this.destroy$)).subscribe({
          next: res => { this.riskIssueReport.set(res); this.loading.set(false); },
          error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải báo cáo'); }
        });
        break;
      case 'quality-kpi':
        if (!this.selectedProjectId) {
          this.error.set('Vui lòng chọn dự án để xem KPI chất lượng');
          this.loading.set(false);
          break;
        }
        this.reportService.getQualityKpi(params).pipe(takeUntil(this.destroy$)).subscribe({
          next: res => { this.qualityKpiReport.set(res); this.loading.set(false); },
          error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Không thể tải báo cáo'); }
        });
        break;
    }
  }

  goToOverduePage(p: number): void {
    if (p < 0) return;
    this.overduePage = p;
    this.loadReport();
  }

  get overduePages(): number[] {
    return Array.from({ length: Math.ceil(this.overdueTotal() / this.overdueSize) }, (_, i) => i);
  }

  maxCount(items: { count: number }[]): number {
    return Math.max(1, ...items.map(i => i.count));
  }

  // ─── Share-link báo cáo tiến độ (GĐ4 cải tiến) ──────────────────
  canShare(): boolean {
    return this.authService.hasPermission('report:share');
  }

  loadShareLinks(): void {
    if (!this.selectedProjectId) return;
    this.shareLinksLoading.set(true);
    this.shareLinksError.set(null);
    this.reportService.getShareLinks(this.selectedProjectId).pipe(takeUntil(this.destroy$)).subscribe({
      next: links => { this.shareLinks.set(links); this.shareLinksLoading.set(false); },
      error: err => { this.shareLinksLoading.set(false); this.shareLinksError.set(err?.error?.message || 'Không thể tải danh sách link'); }
    });
  }

  openShareForm(): void {
    this.newShareLabel = '';
    this.newShareExpiresAt = '';
    this.showShareForm.set(true);
  }

  cancelShareForm(): void {
    this.showShareForm.set(false);
  }

  createShareLink(): void {
    if (!this.selectedProjectId || this.shareBusy()) return;
    this.shareBusy.set(true);
    this.shareLinksError.set(null);
    this.reportService.createShareLink(this.selectedProjectId, {
      label: this.newShareLabel || undefined,
      expiresAt: this.newShareExpiresAt || undefined
    }).subscribe({
      next: () => {
        this.shareBusy.set(false);
        this.showShareForm.set(false);
        this.loadShareLinks();
      },
      error: err => {
        this.shareBusy.set(false);
        this.shareLinksError.set(err?.error?.message || 'Tạo link chia sẻ thất bại');
      }
    });
  }

  revokeShareLink(link: ReportShareLink): void {
    if (!confirm(`Thu hồi link chia sẻ "${link.label || link.id}"? Stakeholder đang có link này sẽ không xem được nữa.`)) return;
    this.shareBusy.set(true);
    this.reportService.revokeShareLink(link.id).subscribe({
      next: () => { this.shareBusy.set(false); this.loadShareLinks(); },
      error: err => { this.shareBusy.set(false); this.shareLinksError.set(err?.error?.message || 'Thu hồi link thất bại'); }
    });
  }

  shareLinkUrl(link: ReportShareLink): string {
    return `${window.location.origin}/public/shared-reports/${link.id}`;
  }

  copyShareLink(link: ReportShareLink): void {
    navigator.clipboard.writeText(this.shareLinkUrl(link)).then(() => {
      this.copiedLinkId.set(link.id);
      setTimeout(() => this.copiedLinkId.set(null), 2000);
    });
  }

  exportReport(): void {
    this.exporting.set(true);
    this.error.set(null);
    const report = this.activeTab() as ReportType;
    this.reportService.exportReport(report, 'csv', {
      projectId: this.selectedProjectId || undefined,
      fromDate: this.fromDate || undefined,
      toDate: this.toDate || undefined,
    }).subscribe({
      next: blob => {
        this.exporting.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `report-${report}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: err => {
        this.exporting.set(false);
        this.error.set(err?.error?.message || 'Xuất báo cáo thất bại');
      }
    });
  }
}
