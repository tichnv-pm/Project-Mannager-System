import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-priority-chip',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="chip" [ngClass]="priorityClass">
      {{ priorityLabel }}
    </span>
  `,
  styles: [`
    .chip {
      display: inline-flex;
      align-items: center;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .prio-low {
      background: rgba(148, 163, 184, 0.2);
      color: #cbd5e1;
    }
    .prio-medium {
      background: rgba(59, 130, 246, 0.2);
      color: #93c5fd;
    }
    .prio-high {
      background: rgba(249, 115, 22, 0.2);
      color: #fdba74;
    }
    .prio-urgent, .prio-critical {
      background: rgba(239, 68, 68, 0.2);
      color: #fca5a5;
      font-weight: 700;
    }
  `]
})
export class PriorityChipComponent {
  @Input({ required: true }) priority!: string;

  get priorityClass(): string {
    if (!this.priority) return 'prio-low';
    return 'prio-' + this.priority.toLowerCase();
  }

  get priorityLabel(): string {
    if (!this.priority) return '';
    const map: Record<string, string> = {
      'LOW': 'Thấp',
      'MEDIUM': 'Trung bình',
      'HIGH': 'Cao',
      'URGENT': 'Khẩn cấp',
      'CRITICAL': 'Nghiêm trọng'
    };
    return map[this.priority] || this.priority;
  }
}
