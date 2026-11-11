import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProjectDodItemCreateRequest, ProjectDodItemResponse, ProjectDodItemUpdateRequest } from './dod.model';

@Injectable({
  providedIn: 'root'
})
export class DodService {
  private readonly API_URL = '/api/v1/projects';

  constructor(private http: HttpClient) {}

  public list(projectId: string): Observable<ProjectDodItemResponse[]> {
    return this.http.get<ProjectDodItemResponse[]>(`${this.API_URL}/${projectId}/dod-items`);
  }

  public create(projectId: string, request: ProjectDodItemCreateRequest): Observable<ProjectDodItemResponse> {
    return this.http.post<ProjectDodItemResponse>(`${this.API_URL}/${projectId}/dod-items`, request);
  }

  public update(projectId: string, itemId: string, request: ProjectDodItemUpdateRequest): Observable<ProjectDodItemResponse> {
    return this.http.put<ProjectDodItemResponse>(`${this.API_URL}/${projectId}/dod-items/${itemId}`, request);
  }

  public delete(projectId: string, itemId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${projectId}/dod-items/${itemId}`);
  }
}
