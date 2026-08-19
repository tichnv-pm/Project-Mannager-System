import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MilestoneCreateRequest, MilestoneResponse, MilestoneUpdateRequest } from './milestone.model';
import { PageResponse } from '../../core/models/common.model';

@Injectable({
  providedIn: 'root'
})
export class MilestoneService {
  private readonly API_URL = '/api/v1/milestones';

  constructor(private http: HttpClient) {}

  public getMilestones(
    projectId?: string,
    status?: string,
    page = 0,
    size = 12,
    sort = 'plannedDate,asc'
  ): Observable<PageResponse<MilestoneResponse>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (projectId) params = params.set('projectId', projectId);
    if (status) params = params.set('status', status);

    return this.http.get<PageResponse<MilestoneResponse>>(this.API_URL, { params });
  }

  public getMilestone(id: string): Observable<MilestoneResponse> {
    return this.http.get<MilestoneResponse>(`${this.API_URL}/${id}`);
  }

  public createMilestone(request: MilestoneCreateRequest): Observable<MilestoneResponse> {
    return this.http.post<MilestoneResponse>(this.API_URL, request);
  }

  public updateMilestone(id: string, request: MilestoneUpdateRequest): Observable<MilestoneResponse> {
    return this.http.put<MilestoneResponse>(`${this.API_URL}/${id}`, request);
  }

  public deleteMilestone(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
