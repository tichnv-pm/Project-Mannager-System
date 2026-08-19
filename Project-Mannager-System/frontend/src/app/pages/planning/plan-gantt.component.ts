import { Component, Input, OnInit, OnChanges, SimpleChanges, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PlanService } from './plan.service';
import {
  GanttData,
  GanttTask,
  GanttDependency,
  GanttZoom,
  PlanTaskType,
  PlanTaskStatus,
  PLAN_TASK_TYPE_LABELS,
  PLAN_TASK_STATUS_LABELS
} from './plan.model';

export interface CalculatedBar {
  task: GanttTask;
  rowIndex: number;
  x: number;
  y: number;
  width: number;
  height: number;
  progressWidth: number;
  baselineX?: number;
  baselineWidth?: number;
  isMilestone: boolean;
  isSummary: boolean;
  isCritical: boolean;
}

export interface CalculatedArrow {
  fromId: string;
  toId: string;
  pathD: string;
  type: string;
}

export interface TimelineHeader {
  label: string;
  dateStr: string;
  isWeekend: boolean;
}

@Component({
  selector: 'app-plan-gantt',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './plan-gantt.component.html',
  styleUrls: ['./plan-gantt.component.scss']
})
export class PlanGanttComponent implements OnInit, OnChanges {
  @Input({ required: true }) planId!: string;

  private planService = inject(PlanService);

  loading = signal(true);
  error = signal<string | null>(null);
  ganttData = signal<GanttData | null>(null);

  zoom = signal<GanttZoom>('DAY');
  showCriticalHighlight = signal(true);
  showBaselineOverlay = signal(true);
  keyword = signal('');

  // Dimensions
  readonly ROW_HEIGHT = 42;
  readonly HEADER_HEIGHT = 50;

  columnWidth = computed(() => {
    switch (this.zoom()) {
      case 'DAY': return 36;
      case 'WEEK': return 120;
      case 'MONTH': return 180;
      default: return 36;
    }
  });

  filteredTasks = computed(() => {
    const data = this.ganttData();
    if (!data || !data.tasks) return [];
    const kw = this.keyword().toLowerCase().trim();
    if (!kw) return data.tasks;
    return data.tasks.filter(t =>
      t.taskName.toLowerCase().includes(kw) ||
      t.wbsCode.toLowerCase().includes(kw)
    );
  });

  dateRange = computed(() => {
    const tasks = this.filteredTasks();
    let minTime = new Date().getTime();
    let maxTime = minTime + 30 * 86400000;

    if (tasks.length > 0) {
      let min = Infinity;
      let max = -Infinity;
      for (const t of tasks) {
        if (t.start) {
          const s = new Date(t.start).getTime();
          if (!isNaN(s) && s < min) min = s;
        }
        if (t.finish) {
          const f = new Date(t.finish).getTime();
          if (!isNaN(f) && f > max) max = f;
        }
      }
      if (min !== Infinity) minTime = min;
      if (max !== -Infinity && max > minTime) maxTime = max;
    }

    // Add padding days
    const startDate = new Date(minTime);
    startDate.setHours(0, 0, 0, 0);
    startDate.setDate(startDate.getDate() - 2);

    const endDate = new Date(maxTime);
    endDate.setHours(0, 0, 0, 0);
    endDate.setDate(endDate.getDate() + 5);

    return { startDate, endDate };
  });

  timelineHeaders = computed<TimelineHeader[]>(() => {
    const { startDate, endDate } = this.dateRange();
    const headers: TimelineHeader[] = [];
    const curr = new Date(startDate);

    while (curr <= endDate) {
      const dayOfWeek = curr.getDay();
      const isWeekend = (dayOfWeek === 0 || dayOfWeek === 6);
      const d = curr.getDate();
      const m = curr.getMonth() + 1;
      const label = `${d}/${m}`;
      const dateStr = curr.toISOString().split('T')[0];

      headers.push({ label, dateStr, isWeekend });
      curr.setDate(curr.getDate() + 1);
    }
    return headers;
  });

  svgWidth = computed(() => {
    return this.timelineHeaders().length * this.columnWidth();
  });

  svgHeight = computed(() => {
    return this.HEADER_HEIGHT + this.filteredTasks().length * this.ROW_HEIGHT;
  });

  calculatedBars = computed<CalculatedBar[]>(() => {
    const tasks = this.filteredTasks();
    const headers = this.timelineHeaders();
    if (headers.length === 0 || tasks.length === 0) return [];

    const startTime = new Date(headers[0].dateStr).getTime();
    const colW = this.columnWidth();

    return tasks.map((task, idx) => {
      const y = this.HEADER_HEIGHT + idx * this.ROW_HEIGHT + (this.ROW_HEIGHT - 22) / 2;
      const isMilestone = task.taskType === 'MILESTONE';
      const isSummary = task.taskType === 'PHASE' || task.taskType === 'WORK_PACKAGE';
      const isCritical = task.isCritical;

      let x = 10;
      let width = 60;

      if (task.start && task.finish) {
        const tStart = new Date(task.start).getTime();
        const tFinish = new Date(task.finish).getTime();
        const daysFromStart = (tStart - startTime) / 86400000;
        const durDays = Math.max(1, (tFinish - tStart) / 86400000 + 1);

        x = Math.max(0, daysFromStart * colW);
        width = Math.max(isMilestone ? 16 : 24, durDays * colW);
      }

      const progressWidth = Math.min(width, width * (task.percentComplete / 100));

      let baselineX: number | undefined;
      let baselineWidth: number | undefined;

      if (task.baseline && task.baseline.start && task.baseline.finish) {
        const bStart = new Date(task.baseline.start).getTime();
        const bFinish = new Date(task.baseline.finish).getTime();
        const bDaysStart = (bStart - startTime) / 86400000;
        const bDurDays = Math.max(1, (bFinish - bStart) / 86400000 + 1);
        baselineX = Math.max(0, bDaysStart * colW);
        baselineWidth = Math.max(16, bDurDays * colW);
      }

      return {
        task,
        rowIndex: idx,
        x,
        y,
        width,
        height: isMilestone ? 16 : (isSummary ? 14 : 20),
        progressWidth,
        baselineX,
        baselineWidth,
        isMilestone,
        isSummary,
        isCritical
      };
    });
  });

  calculatedArrows = computed<CalculatedArrow[]>(() => {
    const data = this.ganttData();
    if (!data || !data.dependencies) return [];

    const barsMap = new Map<string, CalculatedBar>();
    for (const b of this.calculatedBars()) {
      barsMap.set(b.task.id, b);
    }

    const arrows: CalculatedArrow[] = [];

    for (const dep of data.dependencies) {
      const fromBar = barsMap.get(dep.from);
      const toBar = barsMap.get(dep.to);

      if (fromBar && toBar) {
        const fromX = dep.type === 'FS' || dep.type === 'FF' ? fromBar.x + fromBar.width : fromBar.x;
        const fromY = fromBar.y + fromBar.height / 2;

        const toX = dep.type === 'FS' || dep.type === 'SS' ? toBar.x : toBar.x + toBar.width;
        const toY = toBar.y + toBar.height / 2;

        const midX = (fromX + toX) / 2;
        const pathD = `M ${fromX} ${fromY} H ${midX} V ${toY} H ${toX}`;

        arrows.push({
          fromId: dep.from,
          toId: dep.to,
          pathD,
          type: dep.type
        });
      }
    }
    return arrows;
  });

  ngOnInit(): void {
    if (this.planId) {
      this.loadGanttData();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['planId'] && !changes['planId'].firstChange) {
      this.loadGanttData();
    }
  }

  loadGanttData(): void {
    this.loading.set(true);
    this.error.set(null);

    this.planService.getGanttData(this.planId).subscribe({
      next: (data) => {
        this.ganttData.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải dữ liệu biểu đồ Gantt');
      }
    });
  }

  setZoom(z: GanttZoom): void {
    this.zoom.set(z);
  }

  typeLabel(type: PlanTaskType): string {
    return PLAN_TASK_TYPE_LABELS[type] || type;
  }

  statusLabel(status: PlanTaskStatus): string {
    return PLAN_TASK_STATUS_LABELS[status] || status;
  }
}
