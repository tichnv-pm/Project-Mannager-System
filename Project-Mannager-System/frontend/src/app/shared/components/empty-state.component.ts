import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="empty-state">
      <div class="icon-wrapper">
        <span class="icon">{{ icon || '📋' }}</span>
      </div>
      <h3 class="title">{{ title }}</h3>
      <p *ngIf="description" class="description">{{ description }}</p>
      <div class="actions">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 24px;
      text-align: center;
      background: rgba(30, 41, 59, 0.4);
      border: 1px dashed rgba(255, 255, 255, 0.12);
      border-radius: 16px;
      margin: 16px 0;
    }
    .icon-wrapper {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.05);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
    }
    .icon {
      font-size: 28px;
    }
    .title {
      font-size: 1.125rem;
      font-weight: 600;
      color: #f1f5f9;
      margin: 0 0 8px 0;
    }
    .description {
      font-size: 0.875rem;
      color: #94a3b8;
      max-width: 400px;
      margin: 0 0 20px 0;
    }
    .actions {
      display: flex;
      gap: 12px;
    }
  `]
})
export class EmptyStateComponent {
  @Input() icon?: string;
  @Input({ required: true }) title!: string;
  @Input() description?: string;
}
