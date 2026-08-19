import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PlanService } from './plan.service';
import {
  ChangeHistoryResponse,
  ChangeSuggestionResponse,
  LinkResponse,
  PLAN_LINK_TARGET_TYPE_LABELS,
  PLAN_LINK_TYPE_LABELS,
  PlanTaskResponse,
  SUGGESTION_FIELD_LABELS,
  SUGGESTION_STATUS_LABELS,
  SuggestionStatus
} from './plan.model';

@Component({
  selector: 'app-plan-change',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, EmptyStateComponent, HasPermissionDirective],
  templateUrl: './plan-change.component.html',
  styleUrls: ['./plan-change.component.scss']
})
export class PlanChangeComponent implements OnInit {
  @Input() planId!: string;

  private planService = inject(PlanService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);

  histories = signal<ChangeHistoryResponse[]>([]);
  suggestions = signal<ChangeSuggestionResponse[]>([]);
  tasks = signal<PlanTaskResponse[]>([]);

  busy = signal(false);
  actionError = signal<string | null>(null);

  // suggestion modal
  showSuggestionModal = signal(false);
  suggestionForm: FormGroup;
  changeFields = signal<SuggestionChangeFieldRow[]>([]);

  // links
  selectedTaskId = signal<string | null>(null);
  links = signal<LinkResponse[]>([]);
  linksLoading = signal(false);
  linksError = signal<string | null>(null);
  showLinkModal = signal(false);
  linkForm: FormGroup;

  readonly targetLabels = PLAN_LINK_TARGET_TYPE_LABELS;
  readonly linkTypeLabels = PLAN_LINK_TYPE_LABELS;
  readonly statusLabels = SUGGESTION_STATUS_LABELS;
  readonly fieldLabels = SUGGESTION_FIELD_LABELS;
  readonly fieldOptions = ['plannedStart', 'plannedFinish', 'durationMinutes', 'plannedEffortMinutes', 'percentComplete', 'status'];

  constructor() {
    this.suggestionForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(200)]],
      description: ['', [Validators.required, Validators.maxLength(1000)]],
      sourceType: [''],
      sourceId: ['']
    });
    this.linkForm = this.fb.group({
      targetType: ['EXECUTION_TASK', Validators.required],
      targetId: ['', Validators.required],
      linkType: ['RELATED', Validators.required],
      note: ['', Validators.maxLength(500)],
      isPrimaryExecution: [false]
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planService.getChangeHistories(this.planId).subscribe({
      next: (h) => {
        this.histories.set(h);
        this.planService.getChangeSuggestions(this.planId).subscribe({
          next: (s) => {
            this.suggestions.set(s);
            this.planService.getTasks(this.planId).subscribe({
              next: (t) => {
                this.tasks.set(t);
                this.loading.set(false);
                if (t.length > 0 && !this.selectedTaskId()) {
                  const target = t.find(x => !x.isSummary) || t[0];
                  if (target) {
                    this.selectTask(target.id);
                  }
                }
              },
              error: (err) => {
                this.loading.set(false);
                this.error.set(err.message || 'Không thể tải task');
              }
            });
          },
          error: (err) => {
            this.loading.set(false);
            this.error.set(err.message || 'Không thể tải change suggestions');
          }
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải change history');
      }
    });
  }

  // ─── Suggestion ─────────────────────────────────────────────────
  openCreateSuggestion(): void {
    this.actionError.set(null);
    this.suggestionForm.reset({ targetType: 'EXECUTION_TASK' });
    this.addChangeField();
    this.showSuggestionModal.set(true);
  }

  closeSuggestionModal(): void {
    this.showSuggestionModal.set(false);
  }

  addChangeField(): void {
    this.changeFields.update(rows => [
      ...rows,
      { entityType: 'PLAN_TASK', entityId: '', field: 'percentComplete', oldValue: '', newValue: '' }
    ]);
  }

  removeChangeField(i: number): void {
    this.changeFields.update(rows => rows.filter((_, idx) => idx !== i));
  }

  updateField(i: number, key: keyof Omit<SuggestionChangeFieldRow, 'taskLabel'>, value: string): void {
    this.changeFields.update(rows => rows.map((r, idx) => (idx === i ? { ...r, [key]: value } : r)));
  }

  taskLabel(taskId: string): string {
    const t = this.tasks().find(x => x.id === taskId);
    return t ? `${t.wbsCode} — ${t.taskName}` : taskId;
  }

  targetTypeLabel(t: string): string {
    return this.targetLabels[t as keyof typeof PLAN_LINK_TARGET_TYPE_LABELS] || t;
  }

  linkTypeLabel(t: string): string {
    return this.linkTypeLabels[t as keyof typeof PLAN_LINK_TYPE_LABELS] || t;
  }

  createSuggestion(): void {
    if (this.suggestionForm.invalid || this.busy()) {
      this.suggestionForm.markAllAsTouched();
      return;
    }
    const f = this.suggestionForm.value;
    const suggestedChanges = this.changeFields()
      .filter(r => r.entityId)
      .map(r => ({
        entityType: r.entityType,
        entityId: r.entityId,
        field: r.field,
        oldValue: r.oldValue || undefined,
        newValue: r.newValue || undefined
      }));
    if (suggestedChanges.length === 0) {
      this.actionError.set('Ít nhất một field thay đổi cần chọn task');
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.planService.createChangeSuggestion(this.planId, {
      title: f.title,
      description: f.description,
      ...(f.sourceType ? { sourceType: f.sourceType, ...(f.sourceId ? { sourceId: f.sourceId } : {}) } : {}),
      suggestedChanges
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.showSuggestionModal.set(false);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Tạo suggestion thất bại');
      }
    });
  }

  accept(s: ChangeSuggestionResponse): void {
    this.busy.set(true);
    this.planService.acceptSuggestion(s.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Duyệt suggestion thất bại');
      }
    });
  }

  reject(s: ChangeSuggestionResponse): void {
    if (!confirm(`Từ chối suggestion "${s.title}"?`)) return;
    this.busy.set(true);
    this.planService.rejectSuggestion(s.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Từ chối suggestion thất bại');
      }
    });
  }

  // ─── Links ──────────────────────────────────────────────────────
  selectTask(taskId: string): void {
    this.selectedTaskId.set(taskId);
    this.loadLinks();
  }

  loadLinks(): void {
    const taskId = this.selectedTaskId();
    if (!taskId) {
      this.links.set([]);
      return;
    }
    this.linksLoading.set(true);
    this.linksError.set(null);
    this.planService.getTaskLinks(this.planId, taskId).subscribe({
      next: (links) => {
        this.links.set(links);
        this.linksLoading.set(false);
      },
      error: (err) => {
        this.linksLoading.set(false);
        this.linksError.set(err.message || 'Không thể tải links');
      }
    });
  }

  openCreateLink(): void {
    this.actionError.set(null);
    this.linkForm.reset({ targetType: 'EXECUTION_TASK', linkType: 'RELATED', isPrimaryExecution: false });
    this.showLinkModal.set(true);
  }

  closeLinkModal(): void {
    this.showLinkModal.set(false);
  }

  createLink(): void {
    const taskId = this.selectedTaskId();
    if (!taskId || this.linkForm.invalid || this.busy()) {
      this.linkForm.markAllAsTouched();
      return;
    }
    const f = this.linkForm.value;
    this.busy.set(true);
    this.actionError.set(null);
    this.planService.createLink(this.planId, taskId, {
      targetType: f.targetType,
      targetId: f.targetId,
      linkType: f.linkType,
      ...(f.note ? { note: f.note } : {}),
      ...(f.isPrimaryExecution ? { isPrimaryExecution: true } : {})
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.showLinkModal.set(false);
        this.loadLinks();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Tạo link thất bại');
      }
    });
  }

  deleteLink(link: LinkResponse): void {
    if (!confirm(`Gỡ link ${link.linkType} → ${link.targetType}?`)) return;
    this.busy.set(true);
    this.planService.deleteLink(link.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.loadLinks();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.message || 'Gỡ link thất bại');
      }
    });
  }

  fmtVal(v?: string): string {
    return v == null || v === '' ? '—' : v;
  }

  statusClass(s: SuggestionStatus): string {
    return s === 'APPLIED' ? 'st-applied' : s === 'REJECTED' ? 'st-rejected' : 'st-pending';
  }
}

interface SuggestionChangeFieldRow {
  entityType: string;
  entityId: string;
  field: string;
  oldValue: string;
  newValue: string;
}