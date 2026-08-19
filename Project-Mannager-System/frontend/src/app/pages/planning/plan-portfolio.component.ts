import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PlanService } from './plan.service';
import { PortfolioSummary } from './plan.model';

@Component({
  selector: 'app-plan-portfolio',
  standalone: true,
  imports: [CommonModule, RouterModule, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './plan-portfolio.component.html',
  styleUrls: ['./plan-portfolio.component.scss']
})
export class PlanPortfolioComponent implements OnInit {
  private planService = inject(PlanService);

  loading = signal(true);
  error = signal<string | null>(null);
  data = signal<PortfolioSummary | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planService.getPortfolio().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải portfolio');
      }
    });
  }

  round(v?: number): string {
    return v == null ? '—' : `${Math.round(v * 10) / 10}%`;
  }
}