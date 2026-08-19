import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { PageResponse } from '../models/common.model';

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  content: string;
  entityType?: string;
  entityId?: string;
  isRead: boolean;
  readAt?: string;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface ReadAllResponse {
  updatedCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly API_URL = '/api/v1/notifications';

  private unreadCountSubject = new BehaviorSubject<number>(0);
  public unreadCount$ = this.unreadCountSubject.asObservable();
  public unreadCountSignal = signal<number>(0);

  constructor(private http: HttpClient) {}

  public loadUnreadCount(): Observable<UnreadCountResponse> {
    return this.http.get<UnreadCountResponse>(`${this.API_URL}/unread-count`).pipe(
      tap(res => {
        this.unreadCountSubject.next(res.unreadCount);
        this.unreadCountSignal.set(res.unreadCount);
      }),
      catchError(() => of({ unreadCount: 0 }))
    );
  }

  public getNotifications(unreadOnly = false, page = 0, size = 10): Observable<PageResponse<NotificationItem>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('unreadOnly', unreadOnly);

    return this.http.get<PageResponse<NotificationItem>>(this.API_URL, { params });
  }

  public markAsRead(id: string): Observable<NotificationItem> {
    return this.http.put<NotificationItem>(`${this.API_URL}/${id}/read`, {}).pipe(
      tap(() => this.loadUnreadCount().subscribe())
    );
  }

  public markAllAsRead(): Observable<ReadAllResponse> {
    return this.http.put<ReadAllResponse>(`${this.API_URL}/read-all`, {}).pipe(
      tap(() => {
        this.unreadCountSubject.next(0);
        this.unreadCountSignal.set(0);
      })
    );
  }
}
