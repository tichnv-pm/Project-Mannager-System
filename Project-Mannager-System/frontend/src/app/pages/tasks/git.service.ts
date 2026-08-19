import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GitInfoResponse } from './git.model';

@Injectable({
  providedIn: 'root'
})
export class GitService {
  private readonly API_URL = '/api/v1/tasks';

  constructor(private http: HttpClient) {}

  public getGitInfo(taskId: string): Observable<GitInfoResponse> {
    return this.http.get<GitInfoResponse>(`${this.API_URL}/${taskId}/git`);
  }
}
