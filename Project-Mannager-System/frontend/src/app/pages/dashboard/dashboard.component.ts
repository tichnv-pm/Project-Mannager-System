import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { DashboardService } from './dashboard.service';
import {
  DashboardSummaryResponse,
  ProjectOption,
  ProjectProgressResponse,
  TaskStatsResponse
} from './dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, PageHeaderComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  private dashboardService = inject(DashboardService);

  loading = signal(true);
  error = signal<string | null>(null);

  selectedProjectId = '';
  fromDate = '';
  toDate = '';

  projectsList = signal<ProjectOption[]>([]);
  summary = signal<DashboardSummaryResponse | null>(null);
  taskStats = signal<TaskStatsResponse | null>(null);
  projectProgress = signal<ProjectProgressResponse | null>(null);

  ngOnInit(): void {
    this.loadProjects();
    this.loadDashboardData();
  }

  loadProjects(): void {
    this.dashboardService.getProjectsOptions().subscribe(projects => {
      this.projectsList.set(projects);
    });
  }

  loadDashboardData(): void {
    this.loading.set(true);
    this.error.set(null);

    this.dashboardService.getDashboardData(
      this.selectedProjectId || undefined,
      this.fromDate || undefined,
      this.toDate || undefined
    ).subscribe({
      next: (data) => {
        this.summary.set(data.summary);
        this.taskStats.set(data.taskStats);
        this.projectProgress.set(data.projectProgress);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải dữ liệu Dashboard. Vui lòng kiểm tra lại kết nối.');
      }
    });
  }

  onFilterChange(): void {
    this.loadDashboardData();
  }

  resetFilter(): void {
    this.selectedProjectId = '';
    this.fromDate = '';
    this.toDate = '';
    this.loadDashboardData();
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = {
      'TODO': 'Cần làm',
      'IN_PROGRESS': 'Đang làm',
      'BLOCKED': 'Tắc nghẽn',
      'REVIEW': 'Đang review',
      'DONE': 'Hoàn thành'
    };
    return map[status] || status;
  }

  getPriorityLabel(priority: string): string {
    const map: Record<string, string> = {
      'LOW': 'Thấp',
      'MEDIUM': 'Trung bình',
      'HIGH': 'Cao',
      'URGENT': 'Khẩn cấp',
      'CRITICAL': 'Nghiêm trọng'
    };
    return map[priority] || priority;
  }

  getTotalTaskCount(): number {
    const stats = this.taskStats();
    if (!stats || !stats.tasksByStatus) return 0;
    return stats.tasksByStatus.reduce((acc, item) => acc + item.count, 0);
  }

  getStatusPercentage(count: number): number {
    const total = this.getTotalTaskCount();
    if (total === 0) return 0;
    return Math.round((count / total) * 100);
  }
}
