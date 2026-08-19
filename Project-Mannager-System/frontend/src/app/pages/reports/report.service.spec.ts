import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReportService } from './report.service';

describe('ReportService', () => {
  let service: ReportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should request tasks-by-status with filters', () => {
    service.getTasksByStatus({ projectId: 'p1', fromDate: '2026-01-01', toDate: '2026-01-31' }).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/reports/tasks-by-status'
      && r.params.get('projectId') === 'p1'
      && r.params.get('fromDate') === '2026-01-01'
      && r.params.get('toDate') === '2026-01-31'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ items: [] });
    httpMock.verify();
  });

  it('should request overdue tasks with pagination', () => {
    service.getOverdueTasks({ projectId: 'p1', page: 2, size: 50 }).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/reports/overdue-tasks'
      && r.params.get('page') === '2'
      && r.params.get('size') === '50'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0 });
    httpMock.verify();
  });

  it('should append multiple projectId params for project-progress', () => {
    service.getProjectProgress(['a', 'b']).subscribe();
    const req = httpMock.expectOne(r => r.url === '/api/v1/reports/project-progress');
    expect(req.request.params.getAll('projectId')).toEqual(['a', 'b']);
    req.flush({ items: [] });
    httpMock.verify();
  });

  it('should export report as csv blob', () => {
    service.exportReport('tasks-by-status', 'csv', { projectId: 'p1' }).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/reports/export'
      && r.params.get('report') === 'tasks-by-status'
      && r.params.get('format') === 'csv'
      && r.params.get('projectId') === 'p1'
    );
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['a,b,c']));
    httpMock.verify();
  });
});
