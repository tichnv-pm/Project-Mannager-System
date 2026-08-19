import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SprintCreateRequest, SprintResponse, SprintUpdateRequest } from './sprint.model';

@Injectable({
  providedIn: 'root'
})
export class SprintService {
  private readonly API_URL = '/api/v1';

  constructor(private http: HttpClient) {}

  public getSprints(projectId: string): Observable<SprintResponse[]> {
    return this.http.get<SprintResponse[]>(`${this.API_URL}/projects/${projectId}/sprints`);
  }

  public createSprint(projectId: string, request: SprintCreateRequest): Observable<SprintResponse> {
    return this.http.post<SprintResponse>(`${this.API_URL}/projects/${projectId}/sprints`, request);
  }

  public updateSprint(sprintId: string, request: SprintUpdateRequest): Observable<SprintResponse> {
    return this.http.put<SprintResponse>(`${this.API_URL}/sprints/${sprintId}`, request);
  }

  public deleteSprint(sprintId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/sprints/${sprintId}`);
  }
}
