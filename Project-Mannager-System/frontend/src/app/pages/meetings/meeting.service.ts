import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ActionItemCreateRequest,
  ActionItemResponse,
  ActionItemUpdateRequest,
  MeetingAttachment,
  MeetingCompleteRequest,
  MeetingCreateRequest,
  MeetingResponse,
  MeetingUpdateRequest
} from './meeting.model';
import { PageResponse } from '../../core/models/common.model';

@Injectable({ providedIn: 'root' })
export class MeetingService {
  private readonly API = '/api/v1/meetings';
  private readonly AI_API = '/api/v1/action-items';

  constructor(private http: HttpClient) {}

  // ─── Meetings ───────────────────────────────────────────────────
  getMeetings(params: {
    keyword?: string;
    projectId?: string;
    status?: string;
    fromTime?: string;
    toTime?: string;
    page?: number;
    size?: number;
    sort?: string;
  } = {}): Observable<PageResponse<MeetingResponse>> {
    let p = new HttpParams()
      .set('page', params.page ?? 0)
      .set('size', params.size ?? 12)
      .set('sort', params.sort ?? 'startTime,desc');

    if (params.keyword)   p = p.set('keyword', params.keyword);
    if (params.projectId) p = p.set('projectId', params.projectId);
    if (params.status)    p = p.set('status', params.status);
    if (params.fromTime)  p = p.set('fromTime', params.fromTime);
    if (params.toTime)    p = p.set('toTime', params.toTime);

    return this.http.get<PageResponse<MeetingResponse>>(this.API, { params: p });
  }

  getTodayMeetings(): Observable<MeetingResponse[]> {
    return this.http.get<MeetingResponse[]>(`${this.API}/today`);
  }

  getMeeting(id: string): Observable<MeetingResponse> {
    return this.http.get<MeetingResponse>(`${this.API}/${id}`);
  }

  createMeeting(request: MeetingCreateRequest): Observable<MeetingResponse> {
    return this.http.post<MeetingResponse>(this.API, request);
  }

  updateMeeting(id: string, request: MeetingUpdateRequest): Observable<MeetingResponse> {
    return this.http.put<MeetingResponse>(`${this.API}/${id}`, request);
  }

  /** Hoàn thành họp & khoá biên bản (API 06 §3.6) */
  completeMeeting(id: string, req: MeetingCompleteRequest): Observable<MeetingResponse> {
    return this.http.put<MeetingResponse>(`${this.API}/${id}/complete`, req);
  }

  /** Thêm/bớt người tham gia (API 06 §3.7) */
  updateParticipants(id: string, add: string[], remove: string[]): Observable<MeetingResponse> {
    return this.http.put<MeetingResponse>(`${this.API}/${id}/participants`, { add, remove });
  }

  deleteMeeting(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API}/${id}`);
  }

  // ─── Attachments ────────────────────────────────────────────────
  getAttachments(meetingId: string): Observable<MeetingAttachment[]> {
    return this.http.get<MeetingAttachment[]>(`${this.API}/${meetingId}/attachments`);
  }

  uploadAttachment(meetingId: string, file: File): Observable<MeetingAttachment> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<MeetingAttachment>(`${this.API}/${meetingId}/attachments`, fd);
  }

  deleteAttachment(meetingId: string, attachmentId: string): Observable<void> {
    return this.http.delete<void>(`${this.API}/${meetingId}/attachments/${attachmentId}`);
  }

  // ─── Action Items ────────────────────────────────────────────────
  getActionItems(params: {
    meetingId?: string;
    projectId?: string;
    assigneeId?: string;
    status?: string;
    overdue?: boolean;
    page?: number;
    size?: number;
  } = {}): Observable<PageResponse<ActionItemResponse>> {
    let p = new HttpParams()
      .set('page', params.page ?? 0)
      .set('size', params.size ?? 20);

    if (params.meetingId)  p = p.set('meetingId', params.meetingId);
    if (params.projectId)  p = p.set('projectId', params.projectId);
    if (params.assigneeId) p = p.set('assigneeId', params.assigneeId);
    if (params.status)     p = p.set('status', params.status);
    if (params.overdue)    p = p.set('overdue', 'true');

    return this.http.get<PageResponse<ActionItemResponse>>(this.AI_API, { params: p });
  }

  getOverdueActionItems(): Observable<PageResponse<ActionItemResponse>> {
    return this.http.get<PageResponse<ActionItemResponse>>(`${this.AI_API}/overdue`);
  }

  createActionItem(req: ActionItemCreateRequest): Observable<ActionItemResponse> {
    return this.http.post<ActionItemResponse>(this.AI_API, req);
  }

  updateActionItem(id: string, req: ActionItemUpdateRequest): Observable<ActionItemResponse> {
    return this.http.put<ActionItemResponse>(`${this.AI_API}/${id}`, req);
  }

  deleteActionItem(id: string): Observable<void> {
    return this.http.delete<void>(`${this.AI_API}/${id}`);
  }

  /** Chuyển action item thành task (API 07 §3.7) */
  convertActionItemToTask(id: string, opts?: { dueDate?: string; priority?: string }): Observable<any> {
    return this.http.post<any>(`${this.AI_API}/${id}/convert-to-task`, opts ?? {});
  }
}
