import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../../core/models/common.model';

export interface StatusCountItem {
  status: string;
  count: number;
}

export interface TasksByStatusReport {
  items: StatusCountItem[];
}

export interface AssigneeCountItem {
  assigneeId: string;
  fullName: string;
  count: number;
  doneCount: number;
}

export interface TasksByAssigneeReport {
  items: AssigneeCountItem[];
}

export interface ProjectProgressItem {
  projectId: string;
  code: string;
  name: string;
  progress: number;
  totalTasks: number;
  doneTasks: number;
}

export interface ProjectProgressReport {
  items: ProjectProgressItem[];
}

export interface RiskLevelCount {
  level: string;
  count: number;
}

export interface IssueSeverityCount {
  severity: string;
  count: number;
}

export interface RiskIssueSummaryReport {
  openRisks: number;
  openIssues: number;
  risksByLevel: RiskLevelCount[];
  issuesBySeverity: IssueSeverityCount[];
}

export interface ReportParams {
  projectId?: string;
  projectIds?: string[];
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export type ReportType = 'tasks-by-status' | 'tasks-by-assignee' | 'overdue-tasks' | 'project-progress' | 'risk-issue-summary';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly API = '/api/v1/reports';

  constructor(private http: HttpClient) {}

  getTasksByStatus(p: ReportParams = {}): Observable<TasksByStatusReport> {
    let params = this.buildParams(p);
    return this.http.get<TasksByStatusReport>(`${this.API}/tasks-by-status`, { params });
  }

  getTasksByAssignee(p: ReportParams = {}): Observable<TasksByAssigneeReport> {
    let params = this.buildParams(p);
    return this.http.get<TasksByAssigneeReport>(`${this.API}/tasks-by-assignee`, { params });
  }

  getOverdueTasks(p: ReportParams = {}): Observable<PageResponse<any>> {
    let params = this.buildParams(p);
    if (p.page != null) params = params.set('page', p.page);
    if (p.size != null) params = params.set('size', p.size);
    return this.http.get<PageResponse<any>>(`${this.API}/overdue-tasks`, { params });
  }

  getProjectProgress(projectIds?: string[]): Observable<ProjectProgressReport> {
    let params = new HttpParams();
    (projectIds ?? []).forEach(id => params = params.append('projectId', id));
    return this.http.get<ProjectProgressReport>(`${this.API}/project-progress`, { params });
  }

  getRiskIssueSummary(p: ReportParams = {}): Observable<RiskIssueSummaryReport> {
    let params = this.buildParams(p);
    return this.http.get<RiskIssueSummaryReport>(`${this.API}/risk-issue-summary`, { params });
  }

  exportReport(report: ReportType, format: 'csv' | 'xlsx', p: ReportParams = {}): Observable<Blob> {
    let params = new HttpParams()
      .set('report', report)
      .set('format', format);
    if (p.projectId) params = params.set('projectId', p.projectId);
    if (p.fromDate) params = params.set('fromDate', p.fromDate);
    if (p.toDate) params = params.set('toDate', p.toDate);
    return this.http.get(`${this.API}/export`, { params, responseType: 'blob' });
  }

  private buildParams(p: ReportParams): HttpParams {
    let params = new HttpParams();
    if (p.projectId) params = params.set('projectId', p.projectId);
    if (p.fromDate) params = params.set('fromDate', p.fromDate);
    if (p.toDate) params = params.set('toDate', p.toDate);
    return params;
  }
}
