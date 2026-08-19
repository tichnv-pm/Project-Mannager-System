import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PlanService } from './plan.service';
import {
  CALENDAR_EXCEPTION_TYPE_LABELS,
  CALENDAR_STATUS_LABELS,
  CalendarExceptionResponse,
  CalendarExceptionType,
  CalendarStatus,
  DAY_OF_WEEK_LABELS,
  PlanCalendarResponse,
  WorkingDayRequest
} from './plan.model';

interface CalendarRow {
  calendar: PlanCalendarResponse;
  isDirect: boolean;
  actionError: string | null;
}

@Component({
  selector: 'app-plan-calendar',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './plan-calendar.component.html',
  styleUrls: ['./plan-calendar.component.scss']
})
export class PlanCalendarComponent implements OnInit {
  @Input() planId!: string;

  private planService = inject(PlanService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);
  effective = signal<PlanCalendarResponse | null>(null);
  sourceLabel = signal('');

  planName = '';
  planVersion = 0;
  planCalendarId: string | null = null;

  calendars = signal<CalendarRow[]>([]);
  manageOpen = signal(false);
  manageBusy = signal(false);

  showCalModal = signal(false);
  isEditCal = signal(false);
  editingCalId: string | null = null;
  calFormBusy = signal(false);
  calFormError = signal<string | null>(null);

  showExcModal = signal(false);
  excCalId: string | null = null;
  excBusy = signal(false);
  excError = signal<string | null>(null);

  readonly dayLabels = DAY_OF_WEEK_LABELS;
  readonly dayKeys = [1, 2, 3, 4, 5, 6, 7];
  readonly statusLabels = CALENDAR_STATUS_LABELS;
  readonly statusKeys = Object.keys(CALENDAR_STATUS_LABELS) as CalendarStatus[];
  readonly excTypeLabels = CALENDAR_EXCEPTION_TYPE_LABELS;
  readonly excTypeKeys = Object.keys(CALENDAR_EXCEPTION_TYPE_LABELS) as CalendarExceptionType[];

  calForm: FormGroup;
  excForm: FormGroup;

  constructor() {
    this.calForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      description: [''],
      dailyWorkingHours: [8, [Validators.min(1), Validators.max(24)]],
      timezone: ['Asia/Ho_Chi_Minh', Validators.maxLength(50)],
      status: ['ACTIVE']
    });
    this.excForm = this.fb.group({
      exceptionDate: ['', Validators.required],
      exceptionType: ['NON_WORKING', Validators.required],
      note: ['', Validators.maxLength(200)]
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planService.getPlan(this.planId).subscribe({
      next: (plan) => {
        this.planName = plan.planName;
        this.planVersion = plan.version;
        this.planCalendarId = plan.calendarId || null;
        this.planService.getPlanCalendar(this.planId).subscribe({
          next: (cal) => {
            this.effective.set(cal);
            this.sourceLabel.set(
              this.planCalendarId === cal.id && this.planCalendarId
                ? 'Calendar trực tiếp của plan'
                : cal.parentCalendarId
                  ? 'Kế thừa từ calendar cha (org)'
                  : 'Calendar hệ thống mặc định'
            );
            this.loading.set(false);
          },
          error: () => {
            this.loading.set(false);
            this.error.set('Không có calendar hiệu lực cho kế hoạch này');
          }
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải thông tin kế hoạch');
      }
    });
  }

  // ─── Manage calendars ───────────────────────────────────────
  openManage(): void {
    this.manageOpen.set(true);
    this.manageBusy.set(true);
    this.planService.getCalendars().subscribe({
      next: (cals) => {
        this.calendars.set(
          cals.map((c) => ({ calendar: c, isDirect: c.id === this.planCalendarId, actionError: null }))
        );
        this.manageBusy.set(false);
      },
      error: (err) => {
        this.manageBusy.set(false);
        this.error.set(err.message || 'Không thể tải danh sách calendar');
      }
    });
  }

  closeManage(): void {
    this.manageOpen.set(false);
  }

  openCreateCal(): void {
    this.isEditCal.set(false);
    this.editingCalId = null;
    this.calFormError.set(null);
    this.calForm.reset({
      name: '',
      description: '',
      dailyWorkingHours: 8,
      timezone: 'Asia/Ho_Chi_Minh',
      status: 'ACTIVE'
    });
    this.workingDayToggles = { 1: true, 2: true, 3: true, 4: true, 5: true, 6: false, 7: false };
    this.showCalModal.set(true);
  }

  openEditCal(row: CalendarRow): void {
    const c = row.calendar;
    this.isEditCal.set(true);
    this.editingCalId = c.id;
    this.editingVersion = c.version;
    this.calFormError.set(null);
    this.calForm.patchValue({
      name: c.name,
      description: c.description || '',
      dailyWorkingHours: c.dailyWorkingHours || 8,
      timezone: c.timezone || 'Asia/Ho_Chi_Minh',
      status: c.status
    });
    const toggles: Record<number, boolean> = {};
    for (const d of this.dayKeys) {
      const wd = c.workingDays.find((w) => w.dayOfWeek === d);
      toggles[d] = wd ? wd.isWorking : false;
    }
    this.workingDayToggles = toggles;
    this.showCalModal.set(true);
  }

  closeCalModal(): void {
    this.showCalModal.set(false);
  }

  workingDayToggles: Record<number, boolean> = {};

  isWorking(day: number): boolean {
    return this.workingDayToggles[day] !== false;
  }

  workingDayOf(day: number) {
    return this.effective()?.workingDays.find((w) => w.dayOfWeek === day);
  }

  isWorkingDay(day: number): boolean {
    const wd = this.workingDayOf(day);
    return wd ? wd.isWorking : true;
  }

  toggleDay(day: number): void {
    this.workingDayToggles[day] = !this.isWorking(day);
  }

  private collectWorkingDays(): WorkingDayRequest[] {
    return this.dayKeys.map((d) => ({ dayOfWeek: d, isWorking: this.isWorking(d) }));
  }

  saveCal(): void {
    if (this.calForm.invalid || this.calFormBusy()) {
      this.calForm.markAllAsTouched();
      return;
    }
    const v = this.calForm.value;
    const workingDays = this.collectWorkingDays();
    this.calFormBusy.set(true);
    this.calFormError.set(null);

    const done = (err: { message?: string } | null) => {
      this.calFormBusy.set(false);
      if (err) {
        this.calFormError.set(err.message || 'Lưu calendar thất bại');
        return;
      }
      this.showCalModal.set(false);
      this.openManage();
    };

    if (this.isEditCal() && this.editingCalId) {
      this.planService
        .updateCalendar(this.editingCalId, {
          name: v.name,
          description: v.description || undefined,
          dailyWorkingHours: v.dailyWorkingHours || undefined,
          timezone: v.timezone || undefined,
          status: v.status,
          version: this.editingVersion,
          workingDays
        })
        .subscribe({ next: () => done(null), error: (e) => done(e) });
    } else {
      this.planService
        .createCalendar({
          name: v.name,
          description: v.description || undefined,
          dailyWorkingHours: v.dailyWorkingHours || undefined,
          timezone: v.timezone || undefined,
          workingDays: workingDays.length ? workingDays : undefined
        })
        .subscribe({ next: () => done(null), error: (e) => done(e) });
    }
  }

  editingVersion = 0;

  deleteCal(row: CalendarRow): void {
    if (!confirm(`Xóa calendar "${row.calendar.name}"? Không thể xóa nếu đang được plan tham chiếu.`)) {
      return;
    }
    this.manageBusy.set(true);
    this.planService.deleteCalendar(row.calendar.id).subscribe({
      next: () => this.openManage(),
      error: (err) => {
        this.manageBusy.set(false);
        row.actionError = err.message || 'Xóa calendar thất bại (có thể đang được tham chiếu)';
      }
    });
  }

  assignCal(row: CalendarRow): void {
    if (row.isDirect) return;
    this.manageBusy.set(true);
    this.planService
      .updatePlan(this.planId, {
        planName: this.planName,
        calendarId: row.calendar.id,
        version: this.planVersion
      })
      .subscribe({
        next: () => {
          this.manageBusy.set(false);
          this.closeManage();
          this.load();
        },
        error: (err) => {
          this.manageBusy.set(false);
          row.actionError = err.message || 'Gán calendar thất bại';
        }
      });
  }

  // ─── Exceptions ─────────────────────────────────────────────
  openAddException(cal: PlanCalendarResponse): void {
    this.excCalId = cal.id;
    this.excError.set(null);
    this.excForm.reset({ exceptionDate: '', exceptionType: 'NON_WORKING', note: '' });
    this.showExcModal.set(true);
  }

  openAddExceptionEffective(): void {
    const cal = this.effective();
    if (cal) this.openAddException(cal);
  }

  closeExcModal(): void {
    this.showExcModal.set(false);
  }

  addException(): void {
    if (this.excForm.invalid || this.excBusy() || !this.excCalId) {
      this.excForm.markAllAsTouched();
      return;
    }
    const v = this.excForm.value;
    this.excBusy.set(true);
    this.excError.set(null);
    this.planService
      .addCalendarException(this.excCalId, {
        exceptionDate: v.exceptionDate,
        exceptionType: v.exceptionType,
        note: v.note || undefined
      })
      .subscribe({
        next: () => {
          this.excBusy.set(false);
          this.showExcModal.set(false);
          if (this.showExcModal()) return;
          if (this.manageOpen()) {
            this.openManage();
          }
          this.load();
        },
        error: (err) => {
          this.excBusy.set(false);
          this.excError.set(err.message || 'Thêm exception thất bại');
        }
      });
  }

  fmtTime(t?: string): string {
    if (!t) return '—';
    return t.slice(0, 5);
  }

  excLabel(type: CalendarExceptionType): string {
    return this.excTypeLabels[type];
  }

  sortExceptions(cal: PlanCalendarResponse): CalendarExceptionResponse[] {
    return [...cal.exceptions].sort((a, b) => a.exceptionDate.localeCompare(b.exceptionDate));
  }
}