import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormArray, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { QaService } from './qa.service';
import {
  TestCaseResponse,
  TestCasePriority,
  TestCaseStatus,
  TestRunResponse,
  TestResultResponse,
  TestResultStatus
} from './qa.model';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-project-qa',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    HasPermissionDirective
  ],
  templateUrl: './project-qa.component.html',
  styleUrls: ['./project-qa.component.scss']
})
export class ProjectQaComponent implements OnInit {
  @Input() projectId!: string;

  private qaService = inject(QaService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);
  activeTab = signal<'cases' | 'runs'>('cases');

  // Test Cases
  testCases = signal<TestCaseResponse[]>([]);
  showCaseModal = signal(false);
  isEditCase = signal(false);
  selectedCaseId = signal<string | null>(null);
  testCaseForm: FormGroup;

  // Test Runs
  testRuns = signal<TestRunResponse[]>([]);
  showRunModal = signal(false);
  testRunForm: FormGroup;
  selectedCaseIds = signal<string[]>([]);

  // Execution
  showExecModal = signal(false);
  selectedRun = signal<TestRunResponse | null>(null);
  runResults = signal<TestResultResponse[]>([]);
  executingResult = signal<TestResultResponse | null>(null);
  execForm: FormGroup;

  formSubmitting = signal(false);
  formError = signal<string | null>(null);

  constructor() {
    this.testCaseForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(200)]],
      description: [''],
      preconditions: [''],
      priority: ['MEDIUM', [Validators.required]],
      status: ['DRAFT', [Validators.required]],
      steps: this.fb.array([])
    });

    this.testRunForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['']
    });

    this.execForm = this.fb.group({
      status: ['PASSED', [Validators.required]],
      actualResult: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    if (this.projectId) {
      this.loadData();
    }
  }

  // ─── Data Loading ────────────────────────────────────────────────

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);

    if (this.activeTab() === 'cases') {
      this.qaService.getTestCases(this.projectId).subscribe({
        next: (cases) => {
          this.testCases.set(cases);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message || 'Lỗi tải danh sách kịch bản');
          this.loading.set(false);
        }
      });
    } else {
      this.qaService.getTestRuns(this.projectId).subscribe({
        next: (runs) => {
          this.testRuns.set(runs);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message || 'Lỗi tải danh sách đợt kiểm thử');
          this.loading.set(false);
        }
      });
    }
  }

  switchTab(tab: 'cases' | 'runs'): void {
    this.activeTab.set(tab);
    this.loadData();
  }

  // ─── Test Cases Form Management ─────────────────────────────────

  get steps(): FormArray {
    return this.testCaseForm.get('steps') as FormArray;
  }

  addStep(action = '', expectedResult = ''): void {
    const stepNum = this.steps.length + 1;
    this.steps.push(this.fb.group({
      stepNumber: [stepNum],
      action: [action, [Validators.required]],
      expectedResult: [expectedResult, [Validators.required]]
    }));
  }

  removeStep(index: number): void {
    this.steps.removeAt(index);
    // Reindex remaining steps
    this.steps.controls.forEach((ctrl, idx) => {
      ctrl.patchValue({ stepNumber: idx + 1 });
    });
  }

  openCreateCaseModal(): void {
    this.isEditCase.set(false);
    this.selectedCaseId.set(null);
    this.testCaseForm.reset({
      title: '',
      description: '',
      preconditions: '',
      priority: 'MEDIUM',
      status: 'DRAFT'
    });
    this.steps.clear();
    this.addStep(); // Add at least one step by default
    this.formError.set(null);
    this.showCaseModal.set(true);
  }

  openEditCaseModal(tc: TestCaseResponse): void {
    this.isEditCase.set(true);
    this.selectedCaseId.set(tc.id);
    this.testCaseForm.reset({
      title: tc.title,
      description: tc.description || '',
      preconditions: tc.preconditions || '',
      priority: tc.priority,
      status: tc.status
    });
    this.steps.clear();
    if (tc.steps && tc.steps.length > 0) {
      tc.steps.forEach(step => {
        this.addStep(step.action, step.expectedResult);
      });
    } else {
      this.addStep();
    }
    this.formError.set(null);
    this.showCaseModal.set(true);
  }

  saveTestCase(): void {
    if (this.testCaseForm.invalid || this.formSubmitting()) {
      this.testCaseForm.markAllAsTouched();
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);

    const payload = this.testCaseForm.value;

    if (this.isEditCase()) {
      this.qaService.updateTestCase(this.selectedCaseId()!, payload).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.showCaseModal.set(false);
          this.loadData();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Lỗi cập nhật kịch bản');
        }
      });
    } else {
      this.qaService.createTestCase(this.projectId, payload).subscribe({
        next: () => {
          this.formSubmitting.set(false);
          this.showCaseModal.set(false);
          this.loadData();
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Lỗi tạo kịch bản');
        }
      });
    }
  }

  deleteTestCase(id: string): void {
    if (!confirm('Bạn có chắc muốn xóa kịch bản kiểm thử này?')) {
      return;
    }
    this.qaService.deleteTestCase(id).subscribe({
      next: () => this.loadData(),
      error: (err) => alert(err.message || 'Xóa kịch bản thất bại')
    });
  }

  // ─── Test Runs Management ────────────────────────────────────────

  openCreateRunModal(): void {
    this.testRunForm.reset({
      name: '',
      description: ''
    });
    this.selectedCaseIds.set([]);
    this.formError.set(null);
    this.showRunModal.set(true);
  }

  toggleTestCaseSelection(id: string): void {
    const ids = [...this.selectedCaseIds()];
    const idx = ids.indexOf(id);
    if (idx > -1) {
      ids.splice(idx, 1);
    } else {
      ids.push(id);
    }
    this.selectedCaseIds.set(ids);
  }

  createTestRun(): void {
    if (this.testRunForm.invalid || this.formSubmitting()) {
      this.testRunForm.markAllAsTouched();
      return;
    }

    if (this.selectedCaseIds().length === 0) {
      this.formError.set('Vui lòng chọn ít nhất một kịch bản kiểm thử');
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);

    const payload = {
      ...this.testRunForm.value,
      testCaseIds: this.selectedCaseIds()
    };

    this.qaService.createTestRun(this.projectId, payload).subscribe({
      next: () => {
        this.formSubmitting.set(false);
        this.showRunModal.set(false);
        this.loadData();
      },
      error: (err) => {
        this.formSubmitting.set(false);
        this.formError.set(err.message || 'Lỗi tạo đợt kiểm thử');
      }
    });
  }

  // ─── Test Execution Management ───────────────────────────────────

  openExecModal(run: TestRunResponse): void {
    this.selectedRun.set(run);
    this.executingResult.set(null);
    this.showExecModal.set(true);
    this.loadRunResults(run.id);
  }

  loadRunResults(runId: string): void {
    this.qaService.getTestRunResults(runId).subscribe({
      next: (results) => this.runResults.set(results),
      error: (err) => alert(err.message || 'Lỗi tải chi tiết đợt kiểm thử')
    });
  }

  selectForExecution(result: TestResultResponse): void {
    this.executingResult.set(result);
    this.execForm.reset({
      status: result.status === 'UNTESTED' ? 'PASSED' : result.status,
      actualResult: result.actualResult || ''
    });
  }

  saveExecutionResult(): void {
    if (this.execForm.invalid || !this.executingResult() || !this.selectedRun()) {
      this.execForm.markAllAsTouched();
      return;
    }

    const run = this.selectedRun()!;
    const res = this.executingResult()!;

    this.qaService.updateTestResult(run.id, res.testCaseId, this.execForm.value).subscribe({
      next: (updated) => {
        this.executingResult.set(null);
        this.loadRunResults(run.id);
        
        if (updated.status === 'FAILED') {
          alert(`Cập nhật thành công! Phát hiện kết quả FAILED. Hệ thống đã tự động tạo một Issue Bug.`);
        }
      },
      error: (err) => {
        alert(err.message || 'Cập nhật kết quả thất bại');
      }
    });
  }
}
