export type TestCasePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TestCaseStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED';
export type TestRunStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
export type TestResultStatus = 'UNTESTED' | 'PASSED' | 'FAILED' | 'BLOCKED';

export interface TestStepDto {
  id?: string;
  stepNumber: number;
  action: string;
  expectedResult: string;
}

export interface TestCaseResponse {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  preconditions?: string;
  priority: TestCasePriority;
  status: TestCaseStatus;
  steps: TestStepDto[];
  createdAt: string;
  createdBy?: string;
  updatedAt: string;
  updatedBy?: string;
}

export interface TestCaseCreateRequest {
  title: string;
  description?: string;
  preconditions?: string;
  priority: TestCasePriority;
  steps: TestStepDto[];
}

export interface TestCaseUpdateRequest {
  title: string;
  description?: string;
  preconditions?: string;
  priority: TestCasePriority;
  status: TestCaseStatus;
  steps: TestStepDto[];
}

export interface TestRunResponse {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  status: TestRunStatus;
  createdAt: string;
  createdBy?: string;
  updatedAt: string;
  updatedBy?: string;
}

export interface TestRunCreateRequest {
  name: string;
  description?: string;
  testCaseIds: string[];
}

export interface TestResultResponse {
  id: string;
  testRunId: string;
  testCaseId: string;
  testCaseTitle: string;
  status: TestResultStatus;
  actualResult?: string;
  executedBy?: string;
  executedByName?: string;
  executedAt?: string;
  bugIssueId?: string; // Optional reference to spawned BUG
  bugIssueCode?: string;
}

export interface TestResultUpdateRequest {
  status: TestResultStatus;
  actualResult?: string;
}
