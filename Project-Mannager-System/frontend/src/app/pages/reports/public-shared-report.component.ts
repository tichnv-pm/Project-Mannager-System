import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { PublicProjectProgress, ReportService } from './report.service';

/**
 * Xem báo cáo tiến độ qua share-link, không cần đăng nhập (GĐ4 cải tiến — stakeholder ngoài
 * tổ chức). Route độc lập, không nằm trong MainLayoutComponent/authGuard (app.routes.ts).
 */
@Component({
  selector: 'app-public-shared-report',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './public-shared-report.component.html',
  styleUrls: ['./public-shared-report.component.scss']
})
export class PublicSharedReportComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private reportService = inject(ReportService);

  loading = signal(true);
  error = signal<string | null>(null);
  report = signal<PublicProjectProgress | null>(null);

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.loading.set(false);
      this.error.set('Thiếu token link chia sẻ.');
      return;
    }
    this.reportService.getPublicProjectProgress(token).subscribe({
      next: res => { this.report.set(res); this.loading.set(false); },
      error: () => {
        this.loading.set(false);
        this.error.set('Link chia sẻ không hợp lệ, đã hết hạn, hoặc đã bị thu hồi.');
      }
    });
  }

  print(): void {
    window.print();
  }
}
