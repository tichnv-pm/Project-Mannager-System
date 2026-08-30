import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { AuthService } from '../../core/auth/auth.service';
import { AdminService, PERMISSION_GROUPS, RoleBrief, UserItem, AuditLogItem } from './admin.service';

@Component({
  selector: 'app-admin-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, PageHeaderComponent, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './admin-panel.component.html',
  styleUrls: ['./admin-panel.component.scss']
})
export class AdminPanelComponent implements OnInit {
  private adminService = inject(AdminService);
  private authService = inject(AuthService);
  private destroy$ = new Subject<void>();

  readonly permissionGroups = PERMISSION_GROUPS;

  // ─── Tabs ─────────────────────────────────────────────────────
  activeTab = signal<'users' | 'roles' | 'audit'>('users');

  // ─── Users ────────────────────────────────────────────────────
  users = signal<UserItem[]>([]);
  usersTotal = signal(0);
  usersPage = 0;
  usersSize = 10;
  userKeyword = '';
  userStatusFilter = '';
  usersLoading = signal(false);
  usersError = signal<string | null>(null);

  showUserModal = signal(false);
  isEditMode = signal(false);
  editingUserId = '';
  userForm = { username: '', fullName: '', email: '', password: '', status: 'ACTIVE', roleIds: [] as string[] };
  userFormError = signal<string | null>(null);
  userSubmitting = signal(false);

  availableRoles = signal<RoleBrief[]>([]);

  // ─── Roles ────────────────────────────────────────────────────
  roles = signal<RoleBrief[]>([]);
  rolesLoading = signal(false);
  rolesError = signal<string | null>(null);
  editingRoleId = '';
  roleForm = { name: '', code: '', description: '' };
  editingRolePermissions: string[] = [];
  roleSaving = signal(false);
  roleSaveMsg = signal<string | null>(null);
  isCreateRoleMode = signal(false);

  // ─── Reset password ──────────────────────────────────────────
  showResetPasswordModal = signal(false);
  resetPasswordUserId = '';
  resetPasswordUsername = '';
  resetPasswordForm = { newPassword: '' };
  resetPasswordError = signal<string | null>(null);
  resetPasswordSubmitting = signal(false);

  // ─── Delete confirm ──────────────────────────────────────────
  deletingUserId = '';
  deletingUsername = '';
  showDeleteUserConfirm = signal(false);
  deletingUserSubmitting = signal(false);
  deletingRoleId = '';
  deletingRoleName = '';
  showDeleteRoleConfirm = signal(false);
  deletingRoleSubmitting = signal(false);

  // ─── Audit ────────────────────────────────────────────────────
  auditLogs = signal<AuditLogItem[]>([]);
  auditTotal = signal(0);
  auditPage = 0;
  auditSize = 20;
  auditAction = '';
  auditEntityType = '';
  auditLoading = signal(false);
  auditError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadUsers();
    this.loadRoles();
    this.loadAuditLogs();
  }

  // ═══════════════ USERS ═══════════════
  get currentUserId(): string | null {
    return this.authService.currentUserSignal()?.id ?? null;
  }

  get userPages(): number[] {
    return Array.from({ length: Math.ceil(this.usersTotal() / this.usersSize) }, (_, i) => i);
  }

  switchTab(tab: 'users' | 'roles' | 'audit'): void {
    this.activeTab.set(tab);
    this.userFormError.set(null);
  }

  loadUsers(): void {
    this.usersLoading.set(true);
    this.usersError.set(null);
    this.adminService.getUsers({
      page: this.usersPage,
      size: this.usersSize,
      keyword: this.userKeyword || undefined,
      status: this.userStatusFilter || undefined,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: res => { this.users.set(res.content || []); this.usersTotal.set(res.totalElements || 0); this.usersLoading.set(false); },
      error: err => { this.usersLoading.set(false); this.usersError.set(this.extractError(err, 'Không thể tải danh sách người dùng')); }
    });
  }

  goToUsersPage(p: number): void {
    if (p < 0 || p >= this.userPages.length) return;
    this.usersPage = p;
    this.loadUsers();
  }

  openCreateModal(): void {
    this.isEditMode.set(false);
    this.editingUserId = '';
    this.userForm = { username: '', fullName: '', email: '', password: '', status: 'ACTIVE', roleIds: [] };
    this.userFormError.set(null);
    this.showUserModal.set(true);
  }

  openEditModal(u: UserItem): void {
    this.isEditMode.set(true);
    this.editingUserId = u.id;
    const roleIds = this.availableRoles().filter(r => u.roles.includes(r.code)).map(r => r.id);
    this.userForm = { username: u.username, fullName: u.fullName, email: u.email, password: '', status: u.status, roleIds };
    this.userFormError.set(null);
    this.showUserModal.set(true);
  }

  closeUserModal(): void {
    if (this.userSubmitting()) return;
    this.showUserModal.set(false);
  }

  private extractError(err: any, fallback: string): string {
    if (!err) return fallback;
    if (err.fieldErrors && Array.isArray(err.fieldErrors) && err.fieldErrors.length > 0) {
      return err.fieldErrors.map((f: any) => f.message).join('. ');
    }
    if (err.message) return err.message;
    if (err.error?.fieldErrors && Array.isArray(err.error.fieldErrors) && err.error.fieldErrors.length > 0) {
      return err.error.fieldErrors.map((f: any) => f.message).join('. ');
    }
    if (err.error?.message) return err.error.message;
    return fallback;
  }

  saveUser(): void {
    this.userFormError.set(null);
    const username = (this.userForm.username || '').trim();
    const fullName = (this.userForm.fullName || '').trim();
    const email = (this.userForm.email || '').trim();
    const password = this.userForm.password || '';

    if (!fullName) { this.userFormError.set('Họ tên là bắt buộc'); return; }
    if (!email) { this.userFormError.set('Email là bắt buộc'); return; }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      this.userFormError.set('Email không đúng định dạng');
      return;
    }

    if (this.isEditMode()) {
      const u = this.users().find(x => x.id === this.editingUserId);
      if (!u) { this.userFormError.set('Người dùng không còn tồn tại'); return; }
      this.userSubmitting.set(true);
      this.adminService.updateUser(this.editingUserId, {
        fullName,
        email,
        roleIds: this.userForm.roleIds,
        version: u.version,
      }).pipe(takeUntil(this.destroy$)).subscribe({
        next: () => { this.userSubmitting.set(false); this.showUserModal.set(false); this.loadUsers(); },
        error: err => { this.userSubmitting.set(false); this.userFormError.set(this.extractError(err, 'Cập nhật thất bại')); }
      });
      return;
    }

    if (!username) { this.userFormError.set('Tên đăng nhập là bắt buộc'); return; }
    const usernameRegex = /^[a-zA-Z0-9._-]+$/;
    if (!usernameRegex.test(username)) {
      this.userFormError.set('Tên đăng nhập chỉ gồm chữ cái, số, dấu chấm, gạch dưới hoặc gạch ngang (không dấu, không khoảng trắng)');
      return;
    }

    const strongPassword = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/.test(password);
    if (!password) { this.userFormError.set('Mật khẩu là bắt buộc'); return; }
    if (!strongPassword) {
      this.userFormError.set('Mật khẩu phải từ 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt (VD: Member@123)');
      return;
    }

    this.userSubmitting.set(true);
    this.adminService.createUser({
      username,
      fullName,
      email,
      password,
      status: this.userForm.status as 'ACTIVE' | 'INACTIVE',
      roleIds: this.userForm.roleIds,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.userSubmitting.set(false); this.showUserModal.set(false); this.loadUsers(); },
      error: err => { this.userSubmitting.set(false); this.userFormError.set(this.extractError(err, 'Tạo người dùng thất bại')); }
    });
  }

  toggleUserStatus(u: UserItem): void {
    const next: 'ACTIVE' | 'INACTIVE' = u.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (next === 'INACTIVE' && u.id === this.currentUserId) {
      this.usersError.set('Không thể vô hiệu hóa tài khoản đang đăng nhập');
      return;
    }
    this.adminService.changeUserStatus(u.id, next, u.version).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => this.loadUsers(),
      error: err => this.usersError.set(this.extractError(err, 'Đổi trạng thái thất bại'))
    });
  }

  isRoleChecked(roleId: string): boolean {
    return this.userForm.roleIds.includes(roleId);
  }

  toggleRole(roleId: string): void {
    const i = this.userForm.roleIds.indexOf(roleId);
    if (i >= 0) this.userForm.roleIds.splice(i, 1);
    else this.userForm.roleIds.push(roleId);
  }

  // ═══════════════ ROLES ═══════════════
  loadRoles(): void {
    this.rolesLoading.set(true);
    this.rolesError.set(null);
    this.adminService.getRoles().pipe(takeUntil(this.destroy$)).subscribe({
      next: res => { this.roles.set(res || []); this.availableRoles.set(res || []); this.rolesLoading.set(false); },
      error: err => { this.rolesLoading.set(false); this.rolesError.set(this.extractError(err, 'Không thể tải vai trò')); }
    });
  }

  openRoleEditor(role: RoleBrief): void {
    this.isCreateRoleMode.set(false);
    this.editingRoleId = role.id;
    this.roleForm = { name: role.name, code: role.code, description: role.description || '' };
    this.editingRolePermissions = [...role.permissions];
    this.roleSaveMsg.set(null);
  }

  openCreateRoleModal(): void {
    this.isCreateRoleMode.set(true);
    this.editingRoleId = '';
    this.roleForm = { name: '', code: '', description: '' };
    this.editingRolePermissions = [];
    this.roleSaveMsg.set(null);
  }

  closeRoleEditor(): void {
    if (this.roleSaving()) return;
    this.editingRoleId = '';
  }

  hasPermission(perm: string): boolean {
    return this.editingRolePermissions.includes(perm);
  }

  togglePermission(perm: string): void {
    const i = this.editingRolePermissions.indexOf(perm);
    if (i >= 0) this.editingRolePermissions.splice(i, 1);
    else this.editingRolePermissions.push(perm);
  }

  saveRole(): void {
    this.roleSaveMsg.set(null);
    if (!this.roleForm.name.trim()) { this.roleSaveMsg.set('Tên vai trò là bắt buộc'); return; }
    if (this.isCreateRoleMode() && !this.roleForm.code.trim()) { this.roleSaveMsg.set('Mã vai trò là bắt buộc'); return; }

    this.roleSaving.set(true);
    if (this.isCreateRoleMode()) {
      this.adminService.createRole({
        code: this.roleForm.code.trim(),
        name: this.roleForm.name.trim(),
        description: this.roleForm.description.trim() || undefined,
        permissionCodes: [...this.editingRolePermissions],
      }).pipe(takeUntil(this.destroy$)).subscribe({
        next: () => { this.roleSaving.set(false); this.editingRoleId = ''; this.loadRoles(); },
        error: err => { this.roleSaving.set(false); this.roleSaveMsg.set(this.extractError(err, 'Tạo vai trò thất bại')); }
      });
      return;
    }

    const role = this.roles().find(r => r.id === this.editingRoleId);
    if (!role) { this.roleSaving.set(false); this.roleSaveMsg.set('Vai trò không còn tồn tại'); return; }
    this.adminService.updateRole(this.editingRoleId, {
      name: this.roleForm.name.trim(),
      description: this.roleForm.description.trim() || undefined,
      permissionCodes: [...this.editingRolePermissions],
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.roleSaving.set(false); this.editingRoleId = ''; this.loadRoles(); },
      error: err => { this.roleSaving.set(false); this.roleSaveMsg.set(this.extractError(err, 'Lưu vai trò thất bại')); }
    });
  }

  askDeleteRole(role: RoleBrief): void {
    if (role.isSystem) return;
    this.deletingRoleId = role.id;
    this.deletingRoleName = role.name;
    this.showDeleteRoleConfirm.set(true);
  }

  confirmDeleteRole(): void {
    this.deletingRoleSubmitting.set(true);
    this.adminService.deleteRole(this.deletingRoleId).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.deletingRoleSubmitting.set(false); this.showDeleteRoleConfirm.set(false); this.loadRoles(); },
      error: err => { this.deletingRoleSubmitting.set(false); this.rolesError.set(this.extractError(err, 'Xóa vai trò thất bại')); this.showDeleteRoleConfirm.set(false); }
    });
  }

  // ═══════════════ USER DELETE & RESET PASSWORD ═══════════════
  askDeleteUser(u: UserItem): void {
    if (u.id === this.currentUserId || u.username === 'admin') return;
    this.deletingUserId = u.id;
    this.deletingUsername = u.username;
    this.showDeleteUserConfirm.set(true);
  }

  confirmDeleteUser(): void {
    this.deletingUserSubmitting.set(true);
    this.adminService.deleteUser(this.deletingUserId).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.deletingUserSubmitting.set(false); this.showDeleteUserConfirm.set(false); this.loadUsers(); },
      error: err => { this.deletingUserSubmitting.set(false); this.usersError.set(this.extractError(err, 'Xóa người dùng thất bại')); this.showDeleteUserConfirm.set(false); }
    });
  }

  openResetPasswordModal(u: UserItem): void {
    this.resetPasswordUserId = u.id;
    this.resetPasswordUsername = u.username;
    this.resetPasswordForm = { newPassword: '' };
    this.resetPasswordError.set(null);
    this.showResetPasswordModal.set(true);
  }

  closeResetPasswordModal(): void {
    if (this.resetPasswordSubmitting()) return;
    this.showResetPasswordModal.set(false);
  }

  submitResetPassword(): void {
    this.resetPasswordError.set(null);
    const pwd = (this.resetPasswordForm.newPassword || '').trim();
    const strong = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/.test(pwd);
    if (!pwd) { this.resetPasswordError.set('Mật khẩu mới là bắt buộc'); return; }
    if (!strong) { this.resetPasswordError.set('Mật khẩu phải từ 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt (VD: Member@123)'); return; }

    this.resetPasswordSubmitting.set(true);
    this.adminService.resetPassword(this.resetPasswordUserId, pwd).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.resetPasswordSubmitting.set(false); this.showResetPasswordModal.set(false); },
      error: err => { this.resetPasswordSubmitting.set(false); this.resetPasswordError.set(this.extractError(err, 'Đặt lại mật khẩu thất bại')); }
    });
  }

  // ═══════════════ DYNAMIC PERMISSION PREVIEW ═══════════════
  get selectedRolePermissions(): string[] {
    const roles = this.availableRoles().filter(r => this.userForm.roleIds.includes(r.id));
    const codes = new Set<string>();
    roles.forEach(r => r.permissions.forEach(p => codes.add(p)));
    return Array.from(codes).sort();
  }

  // ═══════════════ AUDIT ═══════════════
  get auditPages(): number[] {
    return Array.from({ length: Math.ceil(this.auditTotal() / this.auditSize) }, (_, i) => i);
  }

  loadAuditLogs(): void {
    this.auditLoading.set(true);
    this.auditError.set(null);
    this.adminService.getAuditLogs({
      page: this.auditPage,
      size: this.auditSize,
      action: this.auditAction || undefined,
      entityType: this.auditEntityType || undefined,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: res => { this.auditLogs.set(res.content || []); this.auditTotal.set(res.totalElements || 0); this.auditLoading.set(false); },
      error: err => { this.auditLoading.set(false); this.auditError.set(this.extractError(err, 'Không thể tải nhật ký')); }
    });
  }

  goToAuditPage(p: number): void {
    if (p < 0 || p >= this.auditPages.length) return;
    this.auditPage = p;
    this.loadAuditLogs();
  }

  formatInstant(v: string): string {
    if (!v) return '—';
    return new Date(v).toLocaleString('vi-VN');
  }
}
