import { Component, ElementRef, EventEmitter, HostListener, Output, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationItem, NotificationService } from '../../core/notification/notification.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {
  @Output() toggleSidebar = new EventEmitter<void>();

  private elementRef = inject(ElementRef);
  authService = inject(AuthService);
  notificationService = inject(NotificationService);

  showNotificationPanel = signal(false);
  showUserMenu = signal(false);
  notifications = signal<NotificationItem[]>([]);

  get user() {
    return this.authService.currentUserSignal();
  }

  get unreadCount() {
    return this.notificationService.unreadCountSignal();
  }

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.notificationService.loadUnreadCount().subscribe();
    }
  }

  toggleNotifications(): void {
    this.showUserMenu.set(false);
    const nextState = !this.showNotificationPanel();
    this.showNotificationPanel.set(nextState);

    if (nextState) {
      this.notificationService.getNotifications(false, 0, 10).subscribe(res => {
        this.notifications.set(res.content);
      });
    }
  }

  closeNotifications(): void {
    this.showNotificationPanel.set(false);
  }

  toggleUserDropdown(): void {
    this.showNotificationPanel.set(false);
    this.showUserMenu.set(!this.showUserMenu());
  }

  closeUserMenu(): void {
    this.showUserMenu.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.showNotificationPanel.set(false);
      this.showUserMenu.set(false);
    }
  }

  onNotificationClick(item: NotificationItem): void {
    if (!item.isRead) {
      this.notificationService.markAsRead(item.id).subscribe(() => {
        item.isRead = true;
      });
    }
  }

  markAllNotificationsRead(): void {
    this.notificationService.markAllAsRead().subscribe(() => {
      const updated = this.notifications().map(n => ({ ...n, isRead: true }));
      this.notifications.set(updated);
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
