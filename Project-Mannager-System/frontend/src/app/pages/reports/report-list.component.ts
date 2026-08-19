import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ProjectService } from '../projects/project.service';
import { ProjectOption } from '../dashboard/dashboard.model';
import { ReportService, ReportType } from './report.service';

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
  private destroy$ = new Subject<void>();

  // ─── State ────────────────────────────────────────────────────
  activeTab = signal<'tasks-by-status' | 'tasks-by-assignee' | 'overdue-tasks' | 'project-progress' | 'risk-issue-summary'>('tasks-by-status');

  readonly reportTabs: { key: 'tasks-by-status' | 'tasks-by-assignee' | 'overdue-tasks' | 'project-progress' | 'risk-issue-summary'; label: string }[] = [
    { key: 'tasks-by-status', label: '📊 Theo trạng thái' },
    { key: 'tasks-by-assignee', label: '👤 Theo người làm' },
    { key: 'overdue-tasks', label: '⏰ Trễ hạn' },
    { key: 'project-progress', label: '📈 Tiến độ dự án' },
    { key: 'risk-issue-summary', label: '⚠️ Risk & Issue' }
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

  exporting = signal(false);

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

  switchTab(tab: 'tasks-by-status' | 'tasks-by-assignee' | 'overdue-tasks' | 'project-progress' | 'risk-issue-summary'): void {
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
