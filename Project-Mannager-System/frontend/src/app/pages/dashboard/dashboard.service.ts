import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  DashboardSummaryResponse,
  ProjectOption,
  ProjectProgressResponse,
  TaskStatsResponse
} from './dashboard.model';
import { PageResponse } from '../../core/models/common.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly API_URL = '/api/v1/dashboard';

  constructor(private http: HttpClient) {}

  public getDashboardData(
    projectId?: string,
    fromDate?: string,
    toDate?: string
  ): Observable<{
    summary: DashboardSummaryResponse;
    taskStats: TaskStatsResponse;
    projectProgress: ProjectProgressResponse;
  }> {
    let params = new HttpParams();
    if (projectId) params = params.set('projectId', projectId);
    if (fromDate) params = params.set('fromDate', fromDate);
    if (toDate) params = params.set('toDate', toDate);

    const summary$ = this.http.get<DashboardSummaryResponse>(`${this.API_URL}/summary`, { params });
    const taskStats$ = this.http.get<TaskStatsResponse>(`${this.API_URL}/task-stats`, { params });
    
    let prjParams = new HttpParams();
    if (projectId) prjParams = prjParams.set('projectId', projectId);
    const projectProgress$ = this.http.get<ProjectProgressResponse>(`${this.API_URL}/projects/progress`, { params: prjParams });

    return forkJoin({
      summary: summary$,
      taskStats: taskStats$,
      projectProgress: projectProgress$
    });
  }

  public getProjectsOptions(): Observable<ProjectOption[]> {
    return this.http.get<PageResponse<ProjectOption>>('/api/v1/projects', {
      params: new HttpParams().set('page', 0).set('size', 100)
    }).pipe(
      map(res => res.content || []),
      catchError(() => of([]))
    );
  }
}
