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

  toggleNotifications(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
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

  toggleUserDropdown(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.showNotificationPanel.set(false);
    this.showUserMenu.set(!this.showUserMenu());
  }

  closeUserMenu(): void {
    this.showUserMenu.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    const notifWrapper = this.elementRef.nativeElement.querySelector('.notif-wrapper');
    const userWrapper = this.elementRef.nativeElement.querySelector('.user-dropdown-wrapper');

    if (notifWrapper && !notifWrapper.contains(target)) {
      this.showNotificationPanel.set(false);
    }
    if (userWrapper && !userWrapper.contains(target)) {
      this.showUserMenu.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.showNotificationPanel.set(false);
    this.showUserMenu.set(false);
  }

  onNotificationClick(item: NotificationItem): void {
    if (!item.isRead) {
      this.notificationService.markAsRead(item.id).subscribe(() => {
        item.isRead = true;
      });
    }
  }

  markAllNotificationsRead(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.notificationService.markAllAsRead().subscribe(() => {
      const updated = this.notifications().map(n => ({ ...n, isRead: true }));
      this.notifications.set(updated);
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
