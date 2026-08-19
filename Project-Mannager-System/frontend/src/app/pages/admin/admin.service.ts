import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../../core/models/common.model';

export type UserStatus = 'ACTIVE' | 'INACTIVE';

export interface RoleBrief {
  id: string;
  code: string;
  name: string;
  description?: string;
  isSystem: boolean;
  permissions: string[];
}

export interface UserItem {
  id: string;
  username: string;
  fullName: string;
  email: string;
  status: UserStatus;
  createdAt: string;
  version: number;
  roles: string[];
  permissions: string[];
}

export interface AuditLogItem {
  id: string;
  traceId: string;
  actorId: string | null;
  actorUsername: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  beforeData: Record<string, unknown> | null;
  afterData: Record<string, unknown> | null;
  createdAt: string;
}

export interface PermissionOption {
  code: string;
  name: string;
}

export const PERMISSION_GROUPS: { group: string; permissions: PermissionOption[] }[] = [
  { group: 'Người dùng & Vai trò', permissions: [
    { code: 'user:view', name: 'Xem người dùng' },
    { code: 'user:manage', name: 'Quản lý tài khoản' },
    { code: 'role:manage', name: 'Quản lý vai trò & quyền' },
    { code: 'audit:view', name: 'Xem nhật ký hoạt động' }
  ]},
  { group: 'Dự án', permissions: [
    { code: 'project:view', name: 'Xem dự án' },
    { code: 'project:create', name: 'Tạo dự án' },
    { code: 'project:update', name: 'Sửa dự án' },
    { code: 'project:delete', name: 'Xóa dự án' },
    { code: 'project-member:manage', name: 'Quản lý thành viên dự án' }
  ]},
  { group: 'Công việc', permissions: [
    { code: 'task:view', name: 'Xem công việc' },
    { code: 'task:create', name: 'Tạo công việc' },
    { code: 'task:update', name: 'Cập nhật công việc' },
    { code: 'task:delete', name: 'Xóa công việc' },
    { code: 'task:assign', name: 'Giao việc' },
    { code: 'task:comment', name: 'Bình luận' },
    { code: 'task:attachment', name: 'File đính kèm' },
    { code: 'task:export', name: 'Xuất Excel' }
  ]},
  { group: 'Họp & Action Item', permissions: [
    { code: 'meeting:view', name: 'Xem cuộc họp' },
    { code: 'meeting:manage', name: 'Quản lý cuộc họp' },
    { code: 'action-item:view', name: 'Xem action item' },
    { code: 'action-item:manage', name: 'Quản lý action item' }
  ]},
  { group: 'Risk & Issue', permissions: [
    { code: 'risk:view', name: 'Xem risk' },
    { code: 'risk:manage', name: 'Quản lý risk' },
    { code: 'issue:view', name: 'Xem issue' },
    { code: 'issue:manage', name: 'Quản lý issue' }
  ]},
  { group: 'Milestone & Dashboard', permissions: [
    { code: 'milestone:view', name: 'Xem milestone' },
    { code: 'milestone:manage', name: 'Quản lý milestone' },
    { code: 'dashboard:view', name: 'Xem dashboard' }
  ]},
  { group: 'Báo cáo & Thông báo', permissions: [
    { code: 'report:view', name: 'Xem báo cáo' },
    { code: 'report:export', name: 'Xuất báo cáo' },
    { code: 'notification:view', name: 'Xem thông báo' },
    { code: 'notification:manage', name: 'Quản lý thông báo' }
  ]},
  { group: 'Kế hoạch (Project Planning)', permissions: [
    { code: 'plan:view', name: 'Xem kế hoạch, WBS, Gantt, portfolio' },
    { code: 'plan:create', name: 'Tạo kế hoạch (master/detail)' },
    { code: 'plan:update', name: 'Sửa WBS, task, dependency, calendar' },
    { code: 'plan:delete', name: 'Xóa mềm kế hoạch' },
    { code: 'plan:approve', name: 'Duyệt SUBMITTED → APPROVED; kích hoạt ACTIVE' },
    { code: 'plan:version', name: 'Tạo phiên bản kế hoạch (snapshot)' },
    { code: 'plan:baseline', name: 'Tạo baseline + xem variance' },
    { code: 'plan:change', name: 'Tạo/duyệt change history sau APPROVED' },
    { code: 'plan:resource', name: 'Gán resource, chỉnh capacity, xem workload' },
    { code: 'plan:template', name: 'Quản lý template (CRUD/version/clone)' },
    { code: 'plan:link', name: 'Tạo/xóa liên kết plan_links' },
    { code: 'plan:schedule', name: 'Trigger recalc / xem warnings & critical path' }
  ]}
];

export const ALL_PERMISSIONS: string[] = PERMISSION_GROUPS.flatMap(g => g.permissions.map(p => p.code));

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly API = '/api/v1';

  constructor(private http: HttpClient) {}

  // ─── Users ────────────────────────────────────────────────────
  getUsers(p: { page?: number; size?: number; keyword?: string; status?: string; roleCode?: string } = {}): Observable<PageResponse<UserItem>> {
    let params = new HttpParams();
    if (p.page != null) params = params.set('page', p.page);
    if (p.size != null) params = params.set('size', p.size);
    if (p.keyword) params = params.set('keyword', p.keyword);
    if (p.status) params = params.set('status', p.status);
    if (p.roleCode) params = params.set('roleCode', p.roleCode);
    return this.http.get<PageResponse<UserItem>>(`${this.API}/users`, { params });
  }

  createUser(body: { username: string; email: string; fullName: string; password: string; status?: UserStatus; roleIds?: string[] }): Observable<UserItem> {
    return this.http.post<UserItem>(`${this.API}/users`, body);
  }

  updateUser(id: string, body: { fullName: string; email: string; roleIds?: string[]; version: number }): Observable<UserItem> {
    return this.http.put<UserItem>(`${this.API}/users/${id}`, body);
  }

  changeUserStatus(id: string, status: UserStatus, version: number): Observable<UserItem> {
    return this.http.patch<UserItem>(`${this.API}/users/${id}/status`, { status, version });
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API}/users/${id}`);
  }

  resetPassword(userId: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.API}/auth/${userId}/reset-password`, { newPassword });
  }

  // ─── Roles ────────────────────────────────────────────────────
  getRoles(): Observable<RoleBrief[]> {
    return this.http.get<RoleBrief[]>(`${this.API}/roles`);
  }

  createRole(body: { code: string; name: string; description?: string; permissionCodes?: string[] }): Observable<RoleBrief> {
    return this.http.post<RoleBrief>(`${this.API}/roles`, body);
  }

  updateRole(roleId: string, body: { name: string; description?: string; permissionCodes?: string[] }): Observable<RoleBrief> {
    return this.http.put<RoleBrief>(`${this.API}/roles/${roleId}`, body);
  }

  deleteRole(roleId: string): Observable<void> {
    return this.http.delete<void>(`${this.API}/roles/${roleId}`);
  }

  // ─── Audit logs ───────────────────────────────────────────────
  getAuditLogs(p: { page?: number; size?: number; actorId?: string; action?: string; entityType?: string } = {}): Observable<PageResponse<AuditLogItem>> {
    let params = new HttpParams();
    if (p.page != null) params = params.set('page', p.page);
    if (p.size != null) params = params.set('size', p.size);
    if (p.actorId) params = params.set('actorId', p.actorId);
    if (p.action) params = params.set('action', p.action);
    if (p.entityType) params = params.set('entityType', p.entityType);
    return this.http.get<PageResponse<AuditLogItem>>(`${this.API}/audit-logs`, { params });
  }
}
