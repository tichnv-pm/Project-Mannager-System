import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  ProjectCreateRequest,
  ProjectMemberRequest,
  ProjectMemberResponse,
  ProjectResponse,
  ProjectUpdateRequest
} from './project.model';
import { PageResponse } from '../../core/models/common.model';
import { UserBrief } from '../../core/models/auth.model';
import { ProjectOption } from '../dashboard/dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class ProjectService {
  private readonly API_URL = '/api/v1/projects';

  constructor(private http: HttpClient) {}

  public getProjects(
    keyword?: string,
    status?: string,
    myOnly?: boolean,
    page = 0,
    size = 10,
    sort = 'createdAt,desc'
  ): Observable<PageResponse<ProjectResponse>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (keyword) params = params.set('keyword', keyword);
    if (status) params = params.set('status', status);
    if (myOnly !== undefined) params = params.set('myOnly', myOnly);

    return this.http.get<PageResponse<ProjectResponse>>(this.API_URL, { params });
  }

  public getProject(id: string): Observable<ProjectResponse> {
    return this.http.get<ProjectResponse>(`${this.API_URL}/${id}`);
  }

  public createProject(request: ProjectCreateRequest): Observable<ProjectResponse> {
    return this.http.post<ProjectResponse>(this.API_URL, request);
  }

  public updateProject(id: string, request: ProjectUpdateRequest): Observable<ProjectResponse> {
    return this.http.put<ProjectResponse>(`${this.API_URL}/${id}`, request);
  }

  public deleteProject(id: string, confirm = false): Observable<void> {
    let params = new HttpParams();
    if (confirm) params = params.set('confirm', true);
    return this.http.delete<void>(`${this.API_URL}/${id}`, { params });
  }

  public getMembers(projectId: string): Observable<ProjectMemberResponse[]> {
    return this.http.get<ProjectMemberResponse[]>(`${this.API_URL}/${projectId}/members`);
  }

  public getProjectsOptions(): Observable<ProjectOption[]> {
    return this.http.get<PageResponse<ProjectResponse>>(this.API_URL, {
      params: new HttpParams().set('page', 0).set('size', 100)
    }).pipe(
      map(res => (res.content || []).map(p => ({ id: p.id, code: p.code, name: p.name }))),
      catchError(() => of([]))
    );
  }

  public addMember(projectId: string, request: ProjectMemberRequest): Observable<ProjectMemberResponse> {
    return this.http.post<ProjectMemberResponse>(`${this.API_URL}/${projectId}/members`, request);
  }

  public updateMemberRole(projectId: string, userId: string, role: string): Observable<ProjectMemberResponse> {
    return this.http.put<ProjectMemberResponse>(`${this.API_URL}/${projectId}/members/${userId}`, { role });
  }

  public removeMember(projectId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${projectId}/members/${userId}`);
  }

  public getUsersList(): Observable<UserBrief[]> {
    return this.http.get<PageResponse<UserBrief>>('/api/v1/users', {
      params: new HttpParams().set('page', 0).set('size', 100)
    }).pipe(
      map(res => res.content || []),
      catchError(() => of([]))
    );
  }
}
