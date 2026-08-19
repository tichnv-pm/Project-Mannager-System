import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../../core/models/common.model';
import {
  BaselineResponse,
  BaselineVarianceResponse,
  CalendarExceptionRequest,
  CalendarExceptionResponse,
  CapacityUpdateRequest,
  ChangeHistoryResponse,
  ChangeSuggestionCreateRequest,
  ChangeSuggestionResponse,
  CreatePlanFromTemplateRequest,
  CriticalPathResult,
  DependencyCreateRequest,
  DependencyResponse,
  GanttData,
  LinkCreateRequest,
  LinkResponse,
  PlanCalendarCreateRequest,
  PlanCalendarResponse,
  PlanCalendarUpdateRequest,
  PlanCreateRequest,
  PlanResponse,
  PlanStatus,
  PlanTaskCreateRequest,
  PlanTaskMoveRequest,
  PlanTaskResponse,
  PlanTaskUpdateRequest,
  PlanTemplateDetail,
  PlanTemplateResponse,
  PlanType,
  PlanUpdateRequest,
  PortfolioSummary,
  RecalcResponse,
  ResourceAssignmentRequest,
  ResourceAssignmentResponse,
  ResourceAssignmentUpdateRequest,
  ResourceOverviewRow,
  VersionDiffResponse,
  VersionResponse,
  WorkloadGranularity,
  WorkloadResponse
} from './plan.model';

@Injectable({
  providedIn: 'root'
})
export class PlanService {
  private readonly API_URL = '/api/v1/plans';

  constructor(private http: HttpClient) {}

  public getPlans(
    keyword?: string,
    projectId?: string,
    planType?: PlanType,
    status?: PlanStatus,
    page = 0,
    size = 10,
    sort = 'createdAt,desc'
  ): Observable<PageResponse<PlanResponse>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (keyword) params = params.set('keyword', keyword);
    if (projectId) params = params.set('projectId', projectId);
    if (planType) params = params.set('planType', planType);
    if (status) params = params.set('status', status);

    return this.http.get<PageResponse<PlanResponse>>(this.API_URL, { params });
  }

  public getPlan(id: string): Observable<PlanResponse> {
    return this.http.get<PlanResponse>(`${this.API_URL}/${id}`);
  }

  public createPlan(request: PlanCreateRequest): Observable<PlanResponse> {
    return this.http.post<PlanResponse>(this.API_URL, request);
  }

  public updatePlan(id: string, request: PlanUpdateRequest): Observable<PlanResponse> {
    return this.http.put<PlanResponse>(`${this.API_URL}/${id}`, request);
  }

  public deletePlan(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  public submitPlan(id: string): Observable<PlanResponse> {
    return this.http.post<PlanResponse>(`${this.API_URL}/${id}/submit`, {});
  }

  public approvePlan(id: string): Observable<PlanResponse> {
    return this.http.post<PlanResponse>(`${this.API_URL}/${id}/approve`, {});
  }

  public activatePlan(id: string): Observable<PlanResponse> {
    return this.http.post<PlanResponse>(`${this.API_URL}/${id}/activate`, {});
  }

  // ─── WBS / Plan Task ─────────────────────────────────────────
  public getTasks(planId: string): Observable<PlanTaskResponse[]> {
    return this.http.get<PlanTaskResponse[]>(`${this.API_URL}/${planId}/tasks`);
  }

  public createTask(planId: string, request: PlanTaskCreateRequest): Observable<PlanTaskResponse> {
    return this.http.post<PlanTaskResponse>(`${this.API_URL}/${planId}/tasks`, request);
  }

  public updateTask(planId: string, taskId: string, request: PlanTaskUpdateRequest): Observable<PlanTaskResponse> {
    return this.http.put<PlanTaskResponse>(`${this.API_URL}/${planId}/tasks/${taskId}`, request);
  }

  public deleteTask(planId: string, taskId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${planId}/tasks/${taskId}`);
  }

  public moveTask(planId: string, taskId: string, request: PlanTaskMoveRequest): Observable<PlanTaskResponse> {
    return this.http.put<PlanTaskResponse>(`${this.API_URL}/${planId}/tasks/${taskId}/move`, request);
  }

  // ─── Dependency ────────────────────────────────────────────
  public getDependencies(planId: string): Observable<DependencyResponse[]> {
    return this.http.get<DependencyResponse[]>(`${this.API_URL}/${planId}/tasks/dependencies`);
  }

  public createDependency(
    planId: string,
    successorTaskId: string,
    request: DependencyCreateRequest
  ): Observable<DependencyResponse> {
    return this.http.post<DependencyResponse>(
      `${this.API_URL}/${planId}/tasks/${successorTaskId}/dependencies`,
      request
    );
  }

  public deleteDependency(
    planId: string,
    successorTaskId: string,
    dependencyId: string
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.API_URL}/${planId}/tasks/${successorTaskId}/dependencies/${dependencyId}`
    );
  }

  // ─── Working Calendar ───────────────────────────────────────
  public getCalendars(): Observable<PlanCalendarResponse[]> {
    return this.http.get<PlanCalendarResponse[]>('/api/v1/plan-calendars');
  }

  public createCalendar(request: PlanCalendarCreateRequest): Observable<PlanCalendarResponse> {
    return this.http.post<PlanCalendarResponse>('/api/v1/plan-calendars', request);
  }

  public updateCalendar(
    id: string,
    request: PlanCalendarUpdateRequest
  ): Observable<PlanCalendarResponse> {
    return this.http.put<PlanCalendarResponse>(`/api/v1/plan-calendars/${id}`, request);
  }

  public deleteCalendar(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/plan-calendars/${id}`);
  }

  public addCalendarException(
    id: string,
    request: CalendarExceptionRequest
  ): Observable<CalendarExceptionResponse> {
    return this.http.post<CalendarExceptionResponse>(
      `/api/v1/plan-calendars/${id}/exceptions`,
      request
    );
  }

  public getPlanCalendar(planId: string): Observable<PlanCalendarResponse> {
    return this.http.get<PlanCalendarResponse>(`${this.API_URL}/${planId}/calendar`);
  }

  // ─── Scheduling & Critical Path ────────────────────────────
  public recalculatePlan(planId: string): Observable<RecalcResponse> {
    return this.http.post<RecalcResponse>(`${this.API_URL}/${planId}/recalc`, {});
  }

  public getCriticalPath(planId: string): Observable<CriticalPathResult> {
    return this.http.get<CriticalPathResult>(`${this.API_URL}/${planId}/critical-path`);
  }

  // ─── Resource & Workload ────────────────────────────────────
  public getPlanResources(planId: string): Observable<ResourceAssignmentResponse[]> {
    return this.http.get<ResourceAssignmentResponse[]>(`${this.API_URL}/${planId}/resources`);
  }

  public assignResource(
    planId: string,
    taskId: string,
    request: ResourceAssignmentRequest
  ): Observable<ResourceAssignmentResponse> {
    return this.http.post<ResourceAssignmentResponse>(
      `${this.API_URL}/${planId}/tasks/${taskId}/resources`,
      request
    );
  }

  public updateResourceAllocation(
    allocationId: string,
    request: ResourceAssignmentUpdateRequest
  ): Observable<ResourceAssignmentResponse> {
    return this.http.put<ResourceAssignmentResponse>(`/api/v1/resource-allocations/${allocationId}`, request);
  }

  public removeResourceAllocation(allocationId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/resource-allocations/${allocationId}`);
  }

  public updateCapacity(
    resourceId: string,
    request: CapacityUpdateRequest
  ): Observable<unknown> {
    return this.http.put<unknown>(`/api/v1/resources/${resourceId}/capacity`, request);
  }

  public getPlanWorkload(
    planId: string,
    from: string,
    to: string,
    granularity: WorkloadGranularity = 'DAY'
  ): Observable<WorkloadResponse[]> {
    return this.http.get<WorkloadResponse[]>(`${this.API_URL}/${planId}/workload`, {
      params: { from, to, granularity }
    });
  }

  public getResourcesOverview(from: string, to: string): Observable<ResourceOverviewRow[]> {
    return this.http.get<ResourceOverviewRow[]>('/api/v1/resources/overview', {
      params: { from, to }
    });
  }

  // ─── Version & Baseline ─────────────────────────────────────
  public getVersions(planId: string): Observable<VersionResponse[]> {
    return this.http.get<VersionResponse[]>(`${this.API_URL}/${planId}/versions`);
  }

  public createVersion(planId: string, note?: string): Observable<VersionResponse> {
    return this.http.post<VersionResponse>(`${this.API_URL}/${planId}/versions`, { note: note || undefined });
  }

  public getVersionDiff(planId: string, versionNo: number): Observable<VersionDiffResponse> {
    return this.http.get<VersionDiffResponse>(`${this.API_URL}/${planId}/versions/${versionNo}/diff`);
  }

  public getBaselines(planId: string): Observable<BaselineResponse[]> {
    return this.http.get<BaselineResponse[]>(`${this.API_URL}/${planId}/baselines`);
  }

  public createBaseline(planId: string, description?: string): Observable<BaselineResponse> {
    return this.http.post<BaselineResponse>(`${this.API_URL}/${planId}/baselines`, {
      description: description || undefined
    });
  }

  public getBaselineVariance(planId: string, baselineNum: number): Observable<BaselineVarianceResponse> {
    return this.http.get<BaselineVarianceResponse>(
      `${this.API_URL}/${planId}/baselines/${baselineNum}/variance`
    );
  }

  public deleteBaseline(planId: string, baselineNum: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${planId}/baselines/${baselineNum}`);
  }

  // ─── Change suggestion & history ────────────────────────────
  public getChangeHistories(planId: string): Observable<ChangeHistoryResponse[]> {
    return this.http.get<ChangeHistoryResponse[]>(`${this.API_URL}/${planId}/change-histories`);
  }

  public getChangeSuggestions(planId: string): Observable<ChangeSuggestionResponse[]> {
    return this.http.get<ChangeSuggestionResponse[]>(`${this.API_URL}/${planId}/change-suggestions`);
  }

  public createChangeSuggestion(planId: string, request: ChangeSuggestionCreateRequest): Observable<ChangeSuggestionResponse> {
    return this.http.post<ChangeSuggestionResponse>(`${this.API_URL}/${planId}/change-suggestions`, request);
  }

  public acceptSuggestion(suggestionId: string): Observable<ChangeSuggestionResponse> {
    return this.http.post<ChangeSuggestionResponse>(`/api/v1/change-suggestions/${suggestionId}/accept`, {});
  }

  public rejectSuggestion(suggestionId: string): Observable<ChangeSuggestionResponse> {
    return this.http.post<ChangeSuggestionResponse>(`/api/v1/change-suggestions/${suggestionId}/reject`, {});
  }

  // ─── Plan links ─────────────────────────────────────────────
  public getTaskLinks(planId: string, taskId: string): Observable<LinkResponse[]> {
    return this.http.get<LinkResponse[]>(`${this.API_URL}/${planId}/tasks/${taskId}/links`);
  }

  public createLink(planId: string, taskId: string, request: LinkCreateRequest): Observable<LinkResponse> {
    return this.http.post<LinkResponse>(`${this.API_URL}/${planId}/tasks/${taskId}/links`, request);
  }

  public deleteLink(linkId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/links/${linkId}`);
  }

  // ─── Template & Portfolio ─────────────────────────────────────
  public getTemplates(): Observable<PlanTemplateResponse[]> {
    return this.http.get<PlanTemplateResponse[]>('/api/v1/plan-templates');
  }

  public getTemplateDetail(templateId: string): Observable<PlanTemplateDetail> {
    return this.http.get<PlanTemplateDetail>(`/api/v1/plan-templates/${templateId}`);
  }

  public createPlanFromTemplate(request: CreatePlanFromTemplateRequest): Observable<PlanResponse> {
    return this.http.post<PlanResponse>('/api/v1/plans/from-template', request);
  }

  public getPortfolio(): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>('/api/v1/portfolio');
  }

  // ─── Gantt Data ──────────────────────────────────────────────
  public getGanttData(planId: string): Observable<GanttData> {
    return this.http.get<GanttData>(`${this.API_URL}/${planId}/gantt`);
  }

  // ─── Gantt (docs/api/13 §3.3) ────────────────────────────────────
  public getGantt(planId: string): Observable<GanttData> {
    return this.http.get<GanttData>(`${this.API_URL}/${planId}/gantt`);
  }
}