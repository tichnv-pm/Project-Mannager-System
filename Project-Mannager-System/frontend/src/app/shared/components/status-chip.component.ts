import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-status-chip',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="chip" [ngClass]="statusClass">
      <span class="dot"></span>
      {{ statusLabel }}
    </span>
  `,
  styles: [`
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.02em;
    }
    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    .status-todo, .status-not-started, .status-open {
      background: rgba(100, 116, 139, 0.2);
      color: #cbd5e1;
      .dot { background: #94a3b8; }
    }
    .status-in-progress, .status-active, .status-monitoring {
      background: rgba(59, 130, 246, 0.2);
      color: #93c5fd;
      .dot { background: #3b82f6; }
    }
    .status-review, .status-submitted, .status-on-hold {
      background: rgba(245, 158, 11, 0.2);
      color: #fcd34d;
      .dot { background: #f59e0b; }
    }
    .status-draft, .status-archived {
      background: rgba(148, 163, 184, 0.2);
      color: #cbd5e1;
      .dot { background: #94a3b8; }
    }
    .status-approved, .status-done, .status-completed, .status-resolved, .status-closed {
      background: rgba(34, 197, 94, 0.2);
      color: #86efac;
      .dot { background: #22c55e; }
    }
    .status-blocked, .status-delayed, .status-overdue, .status-occurred {
      background: rgba(239, 68, 68, 0.2);
      color: #fca5a5;
      .dot { background: #ef4444; }
    }
    .status-cancelled, .status-rejected {
      background: rgba(148, 163, 184, 0.15);
      color: #94a3b8;
      .dot { background: #64748b; }
    }
  `]
})
export class StatusChipComponent {
  @Input({ required: true }) status!: string;

  get statusClass(): string {
    if (!this.status) return 'status-todo';
    return 'status-' + this.status.toLowerCase().replace(/_/g, '-');
  }

  get statusLabel(): string {
    if (!this.status) return '';
    const map: Record<string, string> = {
      'TODO': 'Cần làm',
      'IN_PROGRESS': 'Đang làm',
      'BLOCKED': 'Bị tắc nghẽn',
      'REVIEW': 'Đang review',
      'DONE': 'Hoàn thành',
      'CANCELLED': 'Đã hủy',
      'NOT_STARTED': 'Chưa bắt đầu',
      'COMPLETED': 'Hoàn tất',
      'DELAYED': 'Trễ hạn',
      'OPEN': 'Đang mở',
      'MONITORING': 'Theo dõi',
      'OCCURRED': 'Đã xảy ra',
      'RESOLVED': 'Đã giải quyết',
      'CLOSED': 'Đã đóng',
      'REJECTED': 'Từ chối',
      'DRAFT': 'Nháp',
      'SUBMITTED': 'Chờ duyệt',
      'APPROVED': 'Đã duyệt',
      'ACTIVE': 'Đang hiệu lực',
      'ON_HOLD': 'Tạm dừng',
      'ARCHIVED': 'Đã lưu trữ'
    };
    return map[this.status] || this.status;
  }
}
