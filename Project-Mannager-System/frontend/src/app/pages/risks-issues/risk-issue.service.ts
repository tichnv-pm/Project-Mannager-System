import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  IssueCreateRequest,
  IssueResponse,
  IssueUpdateRequest,
  RiskCreateRequest,
  RiskResponse,
  RiskUpdateRequest
} from './risk-issue.model';
import { PageResponse } from '../../core/models/common.model';

@Injectable({
  providedIn: 'root'
})
export class RiskIssueService {
  private readonly RISKS_URL = '/api/v1/risks';
  private readonly ISSUES_URL = '/api/v1/issues';

  constructor(private http: HttpClient) {}

  // RISKS API
  public getRisks(
    keyword?: string,
    projectId?: string,
    status?: string,
    level?: string,
    page = 0,
    size = 12,
    sort = 'createdAt,desc'
  ): Observable<PageResponse<RiskResponse>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (keyword) params = params.set('keyword', keyword);
    if (projectId) params = params.set('projectId', projectId);
    if (status) params = params.set('status', status);
    if (level) params = params.set('level', level);

    return this.http.get<PageResponse<RiskResponse>>(this.RISKS_URL, { params });
  }

  public createRisk(request: RiskCreateRequest): Observable<RiskResponse> {
    return this.http.post<RiskResponse>(this.RISKS_URL, request);
  }

  public updateRisk(id: string, request: RiskUpdateRequest): Observable<RiskResponse> {
    return this.http.put<RiskResponse>(`${this.RISKS_URL}/${id}`, request);
  }

  public deleteRisk(id: string): Observable<void> {
    return this.http.delete<void>(`${this.RISKS_URL}/${id}`);
  }

  public convertToIssue(riskId: string): Observable<IssueResponse> {
    return this.http.post<IssueResponse>(`${this.RISKS_URL}/${riskId}/convert-to-issue`, {});
  }

  // ISSUES API
  public getIssues(
    keyword?: string,
    projectId?: string,
    status?: string,
    severity?: string,
    page = 0,
    size = 12,
    sort = 'createdAt,desc'
  ): Observable<PageResponse<IssueResponse>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (keyword) params = params.set('keyword', keyword);
    if (projectId) params = params.set('projectId', projectId);
    if (status) params = params.set('status', status);
    if (severity) params = params.set('severity', severity);

    return this.http.get<PageResponse<IssueResponse>>(this.ISSUES_URL, { params });
  }

  public createIssue(request: IssueCreateRequest): Observable<IssueResponse> {
    return this.http.post<IssueResponse>(this.ISSUES_URL, request);
  }

  public updateIssue(id: string, request: IssueUpdateRequest): Observable<IssueResponse> {
    return this.http.put<IssueResponse>(`${this.ISSUES_URL}/${id}`, request);
  }

  public deleteIssue(id: string): Observable<void> {
    return this.http.delete<void>(`${this.ISSUES_URL}/${id}`);
  }
}
