import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EvmSnapshotResponse, ProjectMemberFinanceResponse } from './finance.model';

@Injectable({
  providedIn: 'root'
})
export class FinanceService {
  private readonly API_URL = '/api/v1/projects';

  constructor(private http: HttpClient) {}

  public getEvmSnapshots(projectId: string): Observable<EvmSnapshotResponse[]> {
    return this.http.get<EvmSnapshotResponse[]>(`${this.API_URL}/${projectId}/finance/evm`);
  }

  public getProjectMembersFinance(projectId: string): Observable<ProjectMemberFinanceResponse[]> {
    return this.http.get<ProjectMemberFinanceResponse[]>(`${this.API_URL}/${projectId}/finance/members`);
  }

  public updateMemberRate(projectId: string, memberId: string, hourlyRate: number): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/${projectId}/finance/members/${memberId}/rate`, { hourlyRate });
  }

  public recalculateEvm(projectId: string, date?: string): Observable<void> {
    const url = `${this.API_URL}/${projectId}/finance/recalculate` + (date ? `?date=${date}` : '');
    return this.http.post<void>(url, {});
  }
}
