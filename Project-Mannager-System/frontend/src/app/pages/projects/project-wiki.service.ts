import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface WikiPageResponse {
  id: string;
  projectId: string;
  parentPageId?: string;
  title: string;
  content: string;
  version: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
}

export interface WikiPageCreateRequest {
  parentPageId?: string;
  title: string;
  content: string;
}

export interface WikiPageUpdateRequest {
  title: string;
  content: string;
  version: number;
}

export interface WikiPageHistoryResponse {
  id: string;
  wikiPageId: string;
  title: string;
  content: string;
  changedBy: string;
  changedByName?: string;
  changedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProjectWikiService {
  private http = inject(HttpClient);
  private apiUrl = '/api/v1';

  getWikiPages(projectId: string): Observable<WikiPageResponse[]> {
    return this.http.get<WikiPageResponse[]>(`${this.apiUrl}/projects/${projectId}/wiki`);
  }

  initializeWiki(projectId: string): Observable<WikiPageResponse[]> {
    return this.http.post<WikiPageResponse[]>(`${this.apiUrl}/projects/${projectId}/wiki/initialize`, {});
  }

  createWikiPage(projectId: string, request: WikiPageCreateRequest): Observable<WikiPageResponse> {
    return this.http.post<WikiPageResponse>(`${this.apiUrl}/projects/${projectId}/wiki`, request);
  }

  updateWikiPage(pageId: string, request: WikiPageUpdateRequest): Observable<WikiPageResponse> {
    return this.http.put<WikiPageResponse>(`${this.apiUrl}/wiki-pages/${pageId}`, request);
  }

  deleteWikiPage(pageId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/wiki-pages/${pageId}`);
  }

  getWikiPageHistory(pageId: string): Observable<WikiPageHistoryResponse[]> {
    return this.http.get<WikiPageHistoryResponse[]>(`${this.apiUrl}/wiki-pages/${pageId}/history`);
  }
}
