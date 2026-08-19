import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminService, PERMISSION_GROUPS, ALL_PERMISSIONS } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should list users with filters', () => {
    service.getUsers({ page: 1, size: 10, keyword: 'tuan', status: 'ACTIVE' }).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/users'
      && r.params.get('page') === '1'
      && r.params.get('size') === '10'
      && r.params.get('keyword') === 'tuan'
      && r.params.get('status') === 'ACTIVE'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, page: 1, totalPages: 0 });
    httpMock.verify();
  });

  it('should create user via POST /users', () => {
    service.createUser({ username: 'u1', email: 'u1@x.com', fullName: 'User 1', password: 'Abc@12345', roleIds: ['r1'] }).subscribe();
    const req = httpMock.expectOne('/api/v1/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ username: 'u1', roleIds: ['r1'] });
    req.flush({ id: 'id1' });
    httpMock.verify();
  });

  it('should update user with version via PUT /users/{id}', () => {
    service.updateUser('id1', { fullName: 'New', email: 'n@x.com', roleIds: [], version: 3 }).subscribe();
    const req = httpMock.expectOne('/api/v1/users/id1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({ version: 3 });
    req.flush({ id: 'id1' });
    httpMock.verify();
  });

  it('should change user status via PATCH /users/{id}/status', () => {
    service.changeUserStatus('id1', 'INACTIVE', 2).subscribe();
    const req = httpMock.expectOne('/api/v1/users/id1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'INACTIVE', version: 2 });
    req.flush({ id: 'id1' });
    httpMock.verify();
  });

  it('should delete user via DELETE /users/{id}', () => {
    service.deleteUser('id1').subscribe();
    const req = httpMock.expectOne('/api/v1/users/id1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should reset password via POST /auth/{id}/reset-password', () => {
    service.resetPassword('id1', 'New@12345').subscribe();
    const req = httpMock.expectOne('/api/v1/auth/id1/reset-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newPassword: 'New@12345' });
    req.flush(null);
    httpMock.verify();
  });

  it('should create role via POST /roles', () => {
    service.createRole({ code: 'QA_LEAD', name: 'QA Lead', description: 'Lead QA', permissionCodes: ['task:view'] }).subscribe();
    const req = httpMock.expectOne('/api/v1/roles');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ code: 'QA_LEAD', name: 'QA Lead' });
    req.flush({ id: 'rid', isSystem: false });
    httpMock.verify();
  });

  it('should update role via PUT /roles/{id}', () => {
    service.updateRole('rid', { name: 'QA Lead', description: 'Desc', permissionCodes: ['task:view'] }).subscribe();
    const req = httpMock.expectOne('/api/v1/roles/rid');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({ name: 'QA Lead', permissionCodes: ['task:view'] });
    req.flush({ id: 'rid', isSystem: false });
    httpMock.verify();
  });

  it('should delete role via DELETE /roles/{id}', () => {
    service.deleteRole('rid').subscribe();
    const req = httpMock.expectOne('/api/v1/roles/rid');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should list audit logs with filters', () => {
    service.getAuditLogs({ page: 0, size: 20, action: 'CREATE', entityType: 'TASK' }).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/audit-logs'
      && r.params.get('action') === 'CREATE'
      && r.params.get('entityType') === 'TASK'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, page: 0, totalPages: 0 });
    httpMock.verify();
  });
});

describe('AdminService permission catalog', () => {
  it('should have unique permission codes across groups', () => {
    const all = ALL_PERMISSIONS;
    expect(new Set(all).size).toBe(all.length);
  });

  it('should keep critical admin permissions in catalog', () => {
    expect(ALL_PERMISSIONS).toContain('user:manage');
    expect(ALL_PERMISSIONS).toContain('role:manage');
    expect(ALL_PERMISSIONS).toContain('audit:view');
  });

  it('should match backend seeded permission count', () => {
    expect(ALL_PERMISSIONS.length).toBeGreaterThanOrEqual(28);
  });

  it('should group every permission exactly once', () => {
    const codes = PERMISSION_GROUPS.flatMap(g => g.permissions.map(p => p.code));
    expect(new Set(codes).size).toBe(codes.length);
    expect(codes.length).toBe(ALL_PERMISSIONS.length);
  });
});
