import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  TaskAssigneeRequest,
  TaskAttachment,
  TaskBlockerRequest,
  TaskCollaboratorsRequest,
  TaskComment,
  TaskCreateRequest,
  TaskDetailResponse,
  TaskFilterParams,
  TaskHistoryEntry,
  TaskProgressRequest,
  TaskStatus,
  TaskStatusRequest,
  TaskSummaryResponse,
  TaskTagsRequest,
  TaskUpdateRequest,
  TaskWatchersRequest
} from './task.model';
import { PageResponse } from '../../core/models/common.model';

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private readonly API_URL = '/api/v1/tasks';

  constructor(private http: HttpClient) {}

  // ─── List / Search ───────────────────────────────────────────────

  public getTasks(params: TaskFilterParams = {}): Observable<PageResponse<TaskSummaryResponse>> {
    let hp = new HttpParams()
      .set('page', params.page ?? 0)
      .set('size', params.size ?? 20)
      .set('sort', params.sort ?? 'createdAt,desc');

    if (params.keyword)      hp = hp.set('keyword', params.keyword);
    if (params.projectId)    hp = hp.set('projectId', params.projectId);
    if (params.assigneeId)   hp = hp.set('assigneeId', params.assigneeId);
    if (params.dueDateFrom)  hp = hp.set('dueDateFrom', params.dueDateFrom);
    if (params.dueDateTo)    hp = hp.set('dueDateTo', params.dueDateTo);
    if (params.overdue !== undefined) hp = hp.set('overdue', params.overdue);
    if (params.blocked !== undefined) hp = hp.set('blocked', params.blocked);
    if (params.status?.length)   params.status.forEach(s => hp = hp.append('status', s));
    if (params.priority?.length) params.priority.forEach(p => hp = hp.append('priority', p));
    if (params.type?.length)     params.type.forEach(t => hp = hp.append('type', t));
    if (params.sprintId)     hp = hp.set('sprintId', params.sprintId);

    return this.http.get<PageResponse<TaskSummaryResponse>>(this.API_URL, { params: hp });
  }

  public getMyTasks(page = 0, size = 20, sort = 'dueDate,asc'): Observable<PageResponse<TaskSummaryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    return this.http.get<PageResponse<TaskSummaryResponse>>(`${this.API_URL}/my-tasks`, { params });
  }

  public getTodayTasks(page = 0, size = 20): Observable<PageResponse<TaskSummaryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<TaskSummaryResponse>>(`${this.API_URL}/today`, { params });
  }

  public getOverdueTasks(page = 0, size = 20): Observable<PageResponse<TaskSummaryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<TaskSummaryResponse>>(`${this.API_URL}/overdue`, { params });
  }

  // ─── CRUD ────────────────────────────────────────────────────────

  public getTask(id: string): Observable<TaskDetailResponse> {
    return this.http.get<TaskDetailResponse>(`${this.API_URL}/${id}`);
  }

  public createTask(request: TaskCreateRequest): Observable<TaskDetailResponse> {
    return this.http.post<TaskDetailResponse>(this.API_URL, request);
  }

  public updateTask(id: string, request: TaskUpdateRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}`, request);
  }

  public deleteTask(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  // ─── Status / Progress / Blocker ─────────────────────────────────

  public updateStatus(id: string, req: TaskStatusRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}/status`, req);
  }

  public updateProgress(id: string, req: TaskProgressRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}/progress`, req);
  }

  public updateBlocker(id: string, req: TaskBlockerRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}/blocker`, req);
  }

  // ─── Assignee / Tags / Collaborators / Watchers ──────────────────

  public updateAssignee(id: string, req: TaskAssigneeRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}/assignee`, req);
  }

  public updateTags(id: string, req: TaskTagsRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}/tags`, req);
  }

  public updateCollaborators(id: string, req: TaskCollaboratorsRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}/collaborators`, req);
  }

  public updateWatchers(id: string, req: TaskWatchersRequest): Observable<TaskDetailResponse> {
    return this.http.put<TaskDetailResponse>(`${this.API_URL}/${id}/watchers`, req);
  }

  // ─── Children ────────────────────────────────────────────────────

  public getChildTasks(id: string): Observable<TaskSummaryResponse[]> {
    return this.http.get<TaskSummaryResponse[]>(`${this.API_URL}/${id}/children`);
  }

  // ─── Comments ────────────────────────────────────────────────────

  public getComments(taskId: string): Observable<TaskComment[]> {
    return this.http.get<TaskComment[]>(`${this.API_URL}/${taskId}/comments`);
  }

  public addComment(taskId: string, content: string): Observable<TaskComment> {
    return this.http.post<TaskComment>(`${this.API_URL}/${taskId}/comments`, { content });
  }

  public updateComment(taskId: string, commentId: string, content: string): Observable<TaskComment> {
    return this.http.put<TaskComment>(`${this.API_URL}/${taskId}/comments/${commentId}`, { content });
  }

  public deleteComment(taskId: string, commentId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${taskId}/comments/${commentId}`);
  }

  // ─── Attachments ─────────────────────────────────────────────────

  public getAttachments(taskId: string): Observable<TaskAttachment[]> {
    return this.http.get<TaskAttachment[]>(`${this.API_URL}/${taskId}/attachments`);
  }

  public uploadAttachment(taskId: string, file: File): Observable<TaskAttachment> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<TaskAttachment>(`${this.API_URL}/${taskId}/attachments`, formData);
  }

  public deleteAttachment(taskId: string, attachmentId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${taskId}/attachments/${attachmentId}`);
  }

  // ─── History ─────────────────────────────────────────────────────

  public getHistory(taskId: string): Observable<TaskHistoryEntry[]> {
    return this.http.get<TaskHistoryEntry[]>(`${this.API_URL}/${taskId}/history`);
  }

  // ─── Export ──────────────────────────────────────────────────────

  public exportExcel(filterParams: TaskFilterParams = {}): Observable<Blob> {
    let hp = new HttpParams();
    if (filterParams.keyword)    hp = hp.set('keyword', filterParams.keyword);
    if (filterParams.projectId)  hp = hp.set('projectId', filterParams.projectId);
    if (filterParams.assigneeId) hp = hp.set('assigneeId', filterParams.assigneeId);
    if (filterParams.status?.length)   filterParams.status.forEach(s => hp = hp.append('status', s));
    if (filterParams.priority?.length) filterParams.priority.forEach(p => hp = hp.append('priority', p));
    return this.http.get(`${this.API_URL}/export`, { params: hp, responseType: 'blob' });
  }
}
