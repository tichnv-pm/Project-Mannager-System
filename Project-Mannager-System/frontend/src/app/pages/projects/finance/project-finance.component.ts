import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../finance.service';
import { EvmSnapshotResponse, ProjectMemberFinanceResponse } from '../finance.model';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-project-finance',
  standalone: true,
  imports: [CommonModule, FormsModule, HasPermissionDirective],
  templateUrl: './project-finance.component.html',
  styleUrls: ['./project-finance.component.scss']
})
export class ProjectFinanceComponent implements OnInit {
  private financeService = inject(FinanceService);

  @Input({ required: true }) projectId!: string;

  loading = signal(true);
  error = signal<string | null>(null);

  snapshots = signal<EvmSnapshotResponse[]>([]);
  members = signal<ProjectMemberFinanceResponse[]>([]);

  latestSnapshot = signal<EvmSnapshotResponse | null>(null);

  recalculating = signal(false);
  
  editingMemberId = signal<string | null>(null);
  editRate = signal<number>(0);
  updatingRate = signal(false);

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);

    this.financeService.getProjectMembersFinance(this.projectId).subscribe({
      next: (m) => {
        this.members.set(m);
        this.loadSnapshots();
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Không thể tải thông tin đơn giá thành viên');
        this.loading.set(false);
      }
    });
  }

  loadSnapshots(): void {
    this.financeService.getEvmSnapshots(this.projectId).subscribe({
      next: (s) => {
        this.snapshots.set(s);
        if (s.length > 0) {
          this.latestSnapshot.set(s[s.length - 1]);
        } else {
          this.latestSnapshot.set(null);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Không thể tải báo cáo tài chính EVM');
        this.loading.set(false);
      }
    });
  }

  startEditRate(member: ProjectMemberFinanceResponse): void {
    this.editingMemberId.set(member.memberId);
    this.editRate.set(member.hourlyRate || 0);
  }

  cancelEditRate(): void {
    this.editingMemberId.set(null);
  }

  saveRate(memberId: string): void {
    if (this.updatingRate()) return;
    this.updatingRate.set(true);

    this.financeService.updateMemberRate(this.projectId, memberId, this.editRate()).subscribe({
      next: () => {
        this.updatingRate.set(false);
        this.editingMemberId.set(null);
        this.loadData();
      },
      error: (err) => {
        this.updatingRate.set(false);
        alert(err?.error?.message || 'Cập nhật đơn giá thất bại');
      }
    });
  }

  recalculateEvm(): void {
    if (this.recalculating()) return;
    this.recalculating.set(true);

    this.financeService.recalculateEvm(this.projectId).subscribe({
      next: () => {
        this.recalculating.set(false);
        this.loadSnapshots();
      },
      error: (err) => {
        this.recalculating.set(false);
        alert(err?.error?.message || 'Tính toán lại thất bại');
      }
    });
  }

  getPvPath(): string {
    return this.getSvgPath(this.snapshots().map(s => s.plannedValue));
  }

  getEvPath(): string {
    return this.getSvgPath(this.snapshots().map(s => s.earnedValue));
  }

  getAcPath(): string {
    return this.getSvgPath(this.snapshots().map(s => s.actualCost));
  }

  getPoints(type: 'pv' | 'ev' | 'ac'): { x: number; y: number; val: number; date: string }[] {
    const list = this.snapshots();
    if (list.length === 0) return [];
    
    const vals = list.map(s => type === 'pv' ? s.plannedValue : (type === 'ev' ? s.earnedValue : s.actualCost));
    const maxVal = Math.max(...this.snapshots().flatMap(s => [s.plannedValue, s.earnedValue, s.actualCost]), 100);
    const minVal = 0;
    const range = maxVal - minVal;

    return list.map((s, idx) => {
      const val = vals[idx];
      const x = list.length === 1 ? 250 : (idx / (list.length - 1)) * 420 + 40;
      const y = 175 - ((val - minVal) / range) * 140;
      return { x, y, val, date: s.snapshotDate };
    });
  }

  private getSvgPath(data: number[]): string {
    if (data.length === 0) return '';
    const maxVal = Math.max(...this.snapshots().flatMap(s => [s.plannedValue, s.earnedValue, s.actualCost]), 100);
    const minVal = 0;
    const range = maxVal - minVal;

    if (data.length === 1) {
      const y = 175 - ((data[0] - minVal) / range) * 140;
      return `M 40 ${y} L 460 ${y}`;
    }

    return data.map((val, idx) => {
      const x = (idx / (data.length - 1)) * 420 + 40;
      const y = 175 - ((val - minVal) / range) * 140;
      return `${idx === 0 ? 'M' : 'L'} ${x} ${y}`;
    }).join(' ');
  }

  getCpiColor(val: number): string {
    if (val >= 1.0) return '#22c55e';
    if (val >= 0.85) return '#eab308';
    return '#ef4444';
  }

  getSpiColor(val: number): string {
    if (val >= 1.0) return '#22c55e';
    if (val >= 0.85) return '#eab308';
    return '#ef4444';
  }
}
