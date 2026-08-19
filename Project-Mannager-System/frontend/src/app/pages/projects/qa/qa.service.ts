import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  TestCaseCreateRequest,
  TestCaseResponse,
  TestCaseUpdateRequest,
  TestRunCreateRequest,
  TestRunResponse,
  TestResultResponse,
  TestResultUpdateRequest
} from './qa.model';

@Injectable({
  providedIn: 'root'
})
export class QaService {
  private readonly API_URL = '/api/v1';

  constructor(private http: HttpClient) {}

  // ─── Test Cases ──────────────────────────────────────────────────

  public getTestCases(projectId: string): Observable<TestCaseResponse[]> {
    return this.http.get<TestCaseResponse[]>(`${this.API_URL}/projects/${projectId}/qa/test-cases`);
  }

  public createTestCase(projectId: string, request: TestCaseCreateRequest): Observable<TestCaseResponse> {
    return this.http.post<TestCaseResponse>(`${this.API_URL}/projects/${projectId}/qa/test-cases`, request);
  }

  public updateTestCase(testCaseId: string, request: TestCaseUpdateRequest): Observable<TestCaseResponse> {
    return this.http.put<TestCaseResponse>(`${this.API_URL}/qa/test-cases/${testCaseId}`, request);
  }

  public deleteTestCase(testCaseId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/qa/test-cases/${testCaseId}`);
  }

  // ─── Test Runs ───────────────────────────────────────────────────

  public getTestRuns(projectId: string): Observable<TestRunResponse[]> {
    return this.http.get<TestRunResponse[]>(`${this.API_URL}/projects/${projectId}/qa/test-runs`);
  }

  public createTestRun(projectId: string, request: TestRunCreateRequest): Observable<TestRunResponse> {
    return this.http.post<TestRunResponse>(`${this.API_URL}/projects/${projectId}/qa/test-runs`, request);
  }

  public getTestRunResults(runId: string): Observable<TestResultResponse[]> {
    return this.http.get<TestResultResponse[]>(`${this.API_URL}/qa/test-runs/${runId}/results`);
  }

  public updateTestResult(runId: string, caseId: string, request: TestResultUpdateRequest): Observable<TestResultResponse> {
    return this.http.put<TestResultResponse>(`${this.API_URL}/qa/test-runs/${runId}/results/${caseId}`, request);
  }
}
