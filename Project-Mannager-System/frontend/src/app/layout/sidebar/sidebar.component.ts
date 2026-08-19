import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, HasPermissionDirective],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  @Input() collapsed = false;
  authService = inject(AuthService);

  get user() {
    return this.authService.currentUserSignal();
  }

  get userRoleLabel(): string {
    const roles = this.user?.roles || [];
    if (roles.includes('ADMIN')) return 'Admin';
    if (roles.includes('PROJECT_MANAGER')) return 'PM';
    if (roles.includes('PROJECT_MEMBER')) return 'Member';
    return 'Viewer';
  }
}
