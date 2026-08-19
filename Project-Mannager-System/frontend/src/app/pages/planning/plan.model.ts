export type PlanType = 'MASTER' | 'DETAIL' | 'TEMPLATE_INSTANCE';
export type PlanStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'ACTIVE'
  | 'ON_HOLD'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'ARCHIVED';

export interface PlanResponse {
  id: string;
  projectId: string;
  planCode: string;
  planName: string;
  description?: string;
  planType: PlanType;
  parentPlanId?: string;
  parentMilestoneTaskId?: string;
  calendarId?: string;
  activeVersionId?: string;
  activeVersionNo?: number;
  plannedStart?: string;
  plannedFinish?: string;
  status: PlanStatus;
  progress: number;
  durationMinutes?: number;
  note?: string;
  createdAt: string;
  version: number;
}

export interface PlanCreateRequest {
  projectId: string;
  planCode: string;
  planName: string;
  planType: PlanType;
  parentPlanId?: string;
  parentMilestoneTaskId?: string;
  calendarId?: string;
  plannedStart?: string;
  plannedFinish?: string;
  description?: string;
}

export interface PlanUpdateRequest {
  planName: string;
  description?: string;
  calendarId?: string;
  plannedStart?: string;
  plannedFinish?: string;
  note?: string;
  version: number;
}

export const PLAN_TYPE_LABELS: Record<PlanType, string> = {
  MASTER: 'Master Plan',
  DETAIL: 'Detail Plan',
  TEMPLATE_INSTANCE: 'Từ Template'
};

export const PLAN_STATUS_LABELS: Record<PlanStatus, string> = {
  DRAFT: 'Nháp',
  SUBMITTED: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  ACTIVE: 'Đang hiệu lực',
  ON_HOLD: 'Tạm dừng',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  ARCHIVED: 'Đã lưu trữ'
};

// ─── WBS / Plan Task ─────────────────────────────────────────────
export type PlanTaskType =
  | 'PHASE'
  | 'SUMMARY_TASK'
  | 'WORK_PACKAGE'
  | 'TASK'
  | 'MILESTONE'
  | 'EXTERNAL_TASK';

export type PlanTaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'DELAYED' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ScheduleMode = 'AUTO' | 'MANUAL';
export type ConstraintType = 'FIXED_DATE' | 'START_NO_EARLIER_THAN' | 'START_NO_LATER_THAN' | 'REMOVE_SCHEDULE';
export type MoveDirection = 'UP' | 'DOWN' | 'INDENT' | 'OUTDENT' | 'TO_PARENT';
export type TimeUnit = 'MINUTE' | 'HOUR' | 'DAY' | 'WEEK' | 'MONTH';

export const SUMMARY_TASK_TYPES: PlanTaskType[] = ['PHASE', 'SUMMARY_TASK', 'WORK_PACKAGE'];
export const LEAF_TASK_TYPES: PlanTaskType[] = ['TASK', 'MILESTONE', 'EXTERNAL_TASK'];

export interface PlanTaskResponse {
  id: string;
  planId: string;
  parentId?: string;
  wbsCode: string;
  taskCode: string;
  taskName: string;
  description?: string;
  taskType: PlanTaskType;
  outlineLevel: number;
  sequenceNumber: number;
  phase?: string;
  workPackage?: string;
  deliverable?: string;
  ownerId?: string;
  plannedStart?: string;
  plannedFinish?: string;
  durationMinutes?: number;
  durationUnit?: TimeUnit;
  plannedEffortMinutes?: number;
  effortUnit?: TimeUnit;
  actualStart?: string;
  actualFinish?: string;
  actualEffortMinutes?: number;
  remainingEffortMinutes?: number;
  percentComplete: number;
  status: PlanTaskStatus;
  priority: TaskPriority;
  scheduleMode: ScheduleMode;
  constraintType?: ConstraintType;
  constraintDate?: string;
  isSummary: boolean;
  isMilestone: boolean;
  isCritical: boolean;
  createdAt: string;
  version: number;
}

export interface PlanTaskCreateRequest {
  parentId?: string;
  taskCode: string;
  taskName: string;
  taskType: PlanTaskType;
  description?: string;
  ownerId?: string;
  plannedStart?: string;
  plannedFinish?: string;
  durationMinutes?: number;
  durationUnit?: TimeUnit;
  plannedEffortMinutes?: number;
  effortUnit?: TimeUnit;
  percentComplete?: number;
  status?: PlanTaskStatus;
  priority?: TaskPriority;
  scheduleMode?: ScheduleMode;
  constraintType?: ConstraintType;
  constraintDate?: string;
  phase?: string;
  workPackage?: string;
  deliverable?: string;
}

export interface PlanTaskUpdateRequest {
  taskName: string;
  description?: string;
  taskType?: PlanTaskType;
  ownerId?: string;
  plannedStart?: string;
  plannedFinish?: string;
  durationMinutes?: number;
  durationUnit?: TimeUnit;
  plannedEffortMinutes?: number;
  effortUnit?: TimeUnit;
  actualStart?: string;
  actualFinish?: string;
  actualEffortMinutes?: number;
  remainingEffortMinutes?: number;
  percentComplete?: number;
  status?: PlanTaskStatus;
  priority?: TaskPriority;
  scheduleMode?: ScheduleMode;
  constraintType?: ConstraintType;
  constraintDate?: string;
  phase?: string;
  workPackage?: string;
  deliverable?: string;
  version: number;
}

export interface PlanTaskMoveRequest {
  direction: MoveDirection;
  targetParentId?: string;
}

// ─── Dependency ──────────────────────────────────────────────
export type DependencyType = 'FS' | 'SS' | 'FF' | 'SF';

export interface DependencyResponse {
  id: string;
  planId: string;
  predecessorTaskId: string;
  predecessorTaskCode: string;
  successorTaskId: string;
  successorTaskCode: string;
  dependencyType: DependencyType;
  lagMinutes: number;
  createdAt: string;
}

export interface DependencyCreateRequest {
  predecessorTaskId: string;
  dependencyType: DependencyType;
  lagMinutes?: number;
}

export const DEPENDENCY_TYPE_LABELS: Record<DependencyType, string> = {
  FS: 'Kết thúc → Bắt đầu (FS)',
  SS: 'Bắt đầu → Bắt đầu (SS)',
  FF: 'Kết thúc → Kết thúc (FF)',
  SF: 'Bắt đầu → Kết thúc (SF)'
};

export const DEPENDENCY_TYPE_SHORT: Record<DependencyType, string> = {
  FS: 'FS',
  SS: 'SS',
  FF: 'FF',
  SF: 'SF'
};

// ─── Working Calendar ────────────────────────────────────────
export type CalendarStatus = 'ACTIVE' | 'INACTIVE';
export type CalendarExceptionType = 'NON_WORKING' | 'WORKING';

export interface WorkingDayResponse {
  id?: string;
  dayOfWeek: number;
  isWorking: boolean;
  startTime?: string;
  endTime?: string;
}

export interface WorkingDayRequest {
  dayOfWeek: number;
  isWorking: boolean;
  startTime?: string;
  endTime?: string;
}

export interface CalendarExceptionResponse {
  id: string;
  exceptionDate: string;
  exceptionType: CalendarExceptionType;
  note?: string;
}

export interface CalendarExceptionRequest {
  exceptionDate: string;
  exceptionType: CalendarExceptionType;
  note?: string;
}

export interface PlanCalendarResponse {
  id: string;
  name: string;
  description?: string;
  parentCalendarId?: string;
  organizationId?: string;
  dailyWorkingHours?: number;
  timezone?: string;
  status: CalendarStatus;
  version: number;
  createdAt: string;
  workingDays: WorkingDayResponse[];
  exceptions: CalendarExceptionResponse[];
}

export interface PlanCalendarCreateRequest {
  name: string;
  description?: string;
  parentCalendarId?: string;
  organizationId?: string;
  dailyWorkingHours?: number;
  timezone?: string;
  workingDays?: WorkingDayRequest[];
}

export interface PlanCalendarUpdateRequest {
  name: string;
  description?: string;
  dailyWorkingHours?: number;
  timezone?: string;
  status: CalendarStatus;
  version: number;
  workingDays?: WorkingDayRequest[];
}

export const DAY_OF_WEEK_LABELS: Record<number, string> = {
  1: 'Thứ 2',
  2: 'Thứ 3',
  3: 'Thứ 4',
  4: 'Thứ 5',
  5: 'Thứ 6',
  6: 'Thứ 7',
  7: 'Chủ nhật'
};

export const CALENDAR_STATUS_LABELS: Record<CalendarStatus, string> = {
  ACTIVE: 'Đang hiệu lực',
  INACTIVE: 'Vô hiệu'
};

export const CALENDAR_EXCEPTION_TYPE_LABELS: Record<CalendarExceptionType, string> = {
  NON_WORKING: 'Nghỉ / Holiday',
  WORKING: 'Làm bù (WORKING)'
};

// ─── Scheduling & Critical Path ──────────────────────────────
export type SchedulingWarningType =
  | 'CONSTRAINT_CONFLICT'
  | 'DATE_NOT_WORKING'
  | 'NEGATIVE_LAG'
  | 'NO_START_ANCHOR'
  | 'CYCLE_DEPENDENCY';

export interface SchedulingWarningDto {
  wbsCode: string;
  type: SchedulingWarningType;
  message: string;
}

export interface RecalcResponse {
  planId: string;
  plannedStart?: string;
  plannedFinish?: string;
  durationMinutes?: number;
  totalTasks: number;
  scheduledTasks: number;
  warnings: SchedulingWarningDto[];
}

export interface CriticalTaskDto {
  taskId: string;
  wbsCode: string;
  taskName: string;
  taskType: PlanTaskType;
  earlyStart?: string;
  earlyFinish?: string;
  lateStart?: string;
  lateFinish?: string;
  totalFloatMinutes: number;
  freeFloatMinutes: number;
  isCritical: boolean;
  criticalPathId?: number;
}

export interface CriticalPathResult {
  planId: string;
  plannedStart?: string;
  plannedFinish?: string;
  totalDurationMinutes?: number;
  thresholdMinutes: number;
  criticalTaskCount: number;
  tasks: CriticalTaskDto[];
}

export const SCHEDULING_WARNING_LABELS: Record<SchedulingWarningType, string> = {
  CONSTRAINT_CONFLICT: 'Xung khắc constraint',
  DATE_NOT_WORKING: 'Ngày không làm việc',
  NEGATIVE_LAG: 'Lag âm',
  NO_START_ANCHOR: 'Thiếu điểm neo',
  CYCLE_DEPENDENCY: 'Vòng lặp dependency'
};

// ─── Resource & Workload ─────────────────────────────────────
export type ResourceType = 'USER' | 'ROLE' | 'EXTERNAL';
export type WorkloadGranularity = 'DAY' | 'WEEK' | 'MONTH';
export type CapacitySource = 'ORG' | 'PROJECT';

export interface ResourceAssignmentRequest {
  resourceType: ResourceType;
  resourceId: string;
  allocationPercent?: number;
  roleOnTask?: string;
  startDate?: string;
  endDate?: string;
  plannedEffortMinutes?: number;
}

export interface ResourceAssignmentResponse {
  id: string;
  planId: string;
  taskId: string;
  taskCode: string;
  taskName: string;
  taskSummary: boolean;
  resourceType: ResourceType;
  resourceId: string;
  resourceName: string;
  roleOnTask?: string;
  allocationPercent: number;
  startDate?: string;
  endDate?: string;
  plannedEffortMinutes?: number;
  overAllocation: boolean;
  utilizationPercent?: number;
}

export interface ResourceAssignmentUpdateRequest {
  allocationPercent?: number;
  roleOnTask?: string;
  startDate?: string;
  endDate?: string;
  plannedEffortMinutes?: number;
}

export interface WorkloadBucket {
  date: string;
  demandMinutes: number;
  capacityMinutes?: number;
  utilizationPercent?: number;
  overAllocation: boolean;
}

export interface WorkloadResponse {
  resourceType: ResourceType;
  resourceId: string;
  resourceName: string;
  granularity: string;
  from: string;
  to: string;
  totalDemandMinutes: number;
  totalCapacityMinutes?: number;
  totalUtilizationPercent?: number;
  overAllocation: boolean;
  buckets: WorkloadBucket[];
}

export interface CapacityUpdateRequest {
  resourceType: ResourceType;
  capacityPercent: number;
  startDate: string;
  endDate?: string;
  source?: CapacitySource;
}

export interface CapacityResponse {
  id: string;
  resourceType: ResourceType;
  resourceId: string;
  capacityPercent: number;
  startDate?: string;
  endDate?: string;
  source: CapacitySource;
}

export interface ResourceOverviewRow {
  resourceType: ResourceType;
  resourceId: string;
  resourceName: string;
  demandMinutes: number;
  capacityMinutes?: number;
  utilizationPercent?: number;
  overAllocation: boolean;
}

export const RESOURCE_TYPE_LABELS: Record<ResourceType, string> = {
  USER: 'Người dùng (USER)',
  ROLE: 'Vai trò (ROLE)',
  EXTERNAL: 'Bên ngoài (EXTERNAL)'
};

export const WORKLOAD_GRANULARITY_LABELS: Record<WorkloadGranularity, string> = {
  DAY: 'Theo ngày',
  WEEK: 'Theo tuần',
  MONTH: 'Theo tháng'
};

// ─── Version & Baseline ──────────────────────────────────────
export interface VersionResponse {
  id: string;
  planId: string;
  versionNo: number;
  status: string;
  note?: string;
  createdAt: string;
  taskCount: number;
  dependencyCount: number;
  resourceCount: number;
  isActive: boolean;
}

export interface TaskDiffResponse {
  wbsCode: string;
  taskName: string;
  field: string;
  fromValue?: unknown;
  toValue?: unknown;
}

export interface VersionDiffResponse {
  versionNo: number;
  compareToVersionNo: number;
  tasks: TaskDiffResponse[];
}

export interface BaselineResponse {
  id: string;
  planId: string;
  baselineNum: number;
  versionNo?: number;
  description?: string;
  capturedAt: string;
  capturedBy?: string;
  taskCount: number;
}

export interface BaselineVarianceRow {
  taskId: string;
  wbsCode: string;
  taskName: string;
  taskType: PlanTaskType;
  baselineStart?: string;
  baselineFinish?: string;
  currentStart?: string;
  currentFinish?: string;
  baselineDurationMinutes?: number;
  currentDurationMinutes?: number;
  baselineEffortMinutes?: number;
  currentEffortMinutes?: number;
  baselineProgress: number;
  currentProgress: number;
  startDifferenceDays?: number;
  finishDifferenceDays?: number;
  durationDifferenceMinutes?: number;
  effortDifferenceMinutes?: number;
  progressDifference: number;
  milestoneDone: boolean;
  taskDeleted: boolean;
}

export interface BaselineVarianceResponse {
  baselineId: string;
  baselineNum: number;
  planId: string;
  planName: string;
  tasks: BaselineVarianceRow[];
}

// ─── Change & Link ─────────────────────────────────────────
export type PlanLinkTargetType = 'EXECUTION_TASK' | 'ISSUE' | 'RISK' | 'MILESTONE';
export type PlanLinkType = 'RELATED' | 'BLOCKED_BY';
export type SuggestionStatus = 'PENDING' | 'APPLIED' | 'REJECTED';

export const PLAN_LINK_TARGET_TYPE_LABELS: Record<PlanLinkTargetType, string> = {
  EXECUTION_TASK: 'Execution Task',
  ISSUE: 'Issue',
  RISK: 'Risk',
  MILESTONE: 'Milestone'
};

export const PLAN_LINK_TYPE_LABELS: Record<PlanLinkType, string> = {
  RELATED: 'Liên quan',
  BLOCKED_BY: 'Chặn bởi'
};

export const SUGGESTION_STATUS_LABELS: Record<SuggestionStatus, string> = {
  PENDING: 'Chờ duyệt',
  APPLIED: 'Đã áp dụng',
  REJECTED: 'Đã từ chối'
};

export const SUGGESTION_FIELD_LABELS: Record<string, string> = {
  plannedStart: 'Ngày bắt đầu',
  plannedFinish: 'Ngày kết thúc',
  durationMinutes: 'Thời lượng (phút)',
  plannedEffortMinutes: 'Công sức (phút)',
  percentComplete: 'Tiến độ %',
  status: 'Trạng thái'
};

export interface LinkCreateRequest {
  targetType: PlanLinkTargetType;
  targetId: string;
  linkType: PlanLinkType;
  note?: string;
  isPrimaryExecution?: boolean;
}

export interface LinkResponse {
  id: string;
  planId: string;
  planningTaskId: string;
  targetType: string;
  targetId: string;
  linkType: string;
  note?: string;
  isPrimaryExecution: boolean;
  createdBy?: string;
  createdAt: string;
}

export interface ChangeHistoryResponse {
  id: string;
  planId: string;
  changeType: string;
  entityType: string;
  entityId?: string;
  fieldChanged?: string;
  oldValue?: string;
  newValue?: string;
  reason?: string;
  changeRequestId?: string;
  changedBy?: string;
  changedAt: string;
}

export interface SuggestionChangeField {
  entityType: string;
  entityId: string;
  field: string;
  oldValue?: string;
  newValue?: string;
}

export interface ChangeSuggestionCreateRequest {
  sourceType?: string;
  sourceId?: string;
  title: string;
  description: string;
  suggestedChanges: SuggestionChangeField[];
}

export interface ChangeSuggestionResponse {
  id: string;
  planId: string;
  sourceType?: string;
  sourceId?: string;
  title: string;
  description: string;
  status: SuggestionStatus;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewedBy2?: string;
  reviewedAt2?: string;
  createdBy?: string;
  createdAt: string;
}

// ─── Template & Portfolio ───────────────────────────────────
export type TemplateType = 'FULL' | 'PARTIAL';
export type TemplateStatus = 'PUBLISHED' | 'DRAFT';

export const TEMPLATE_TYPE_LABELS: Record<TemplateType, string> = {
  FULL: 'Đầy đủ',
  PARTIAL: 'Một phần'
};

export const TEMPLATE_STATUS_LABELS: Record<TemplateStatus, string> = {
  PUBLISHED: 'Đã xuất bản',
  DRAFT: 'Nháp'
};

export interface PlanTemplateResponse {
  id: string;
  templateCode: string;
  templateName: string;
  description?: string;
  templateType: TemplateType;
  category?: string;
  versionNo?: number;
  status: TemplateStatus;
  isBuiltIn?: boolean;
  taskCount: number;
}

export interface PlanTemplateTask {
  id: string;
  parentId?: string;
  taskName: string;
  taskType: PlanTaskType;
  sequenceNo?: number;
  wbsCode: string;
  durationMinutes?: number;
  plannedEffortMinutes?: number;
  scheduleMode?: string;
}

export interface PlanTemplateDetail extends PlanTemplateResponse {
  tasks: PlanTemplateTask[];
}

export interface CreatePlanFromTemplateRequest {
  projectId: string;
  templateId: string;
  planCode: string;
  planName: string;
  planType?: PlanType;
  parentPlanId?: string;
  startDate: string;
}

export interface PortfolioProject {
  id: string;
  code: string;
  name: string;
  pmName?: string;
  status: string;
  plannedStart?: string;
  plannedFinish?: string;
  progress?: number;
  delayDays?: number;
  isOverAllocated?: boolean;
  criticalTaskCount?: number;
  activePlanId?: string;
}

export interface PortfolioMilestone {
  id: string;
  name: string;
  targetDate: string;
  status: string;
  projectId: string;
  projectName: string;
}

export interface PortfolioSummary {
  totalProjects: number;
  activeProjects: number;
  delayedProjects: number;
  overAllocatedResourcesCount: number;
  averageProgress?: number;
  projects: PortfolioProject[];
  upcomingMilestones: PortfolioMilestone[];
}

export const PLAN_TASK_TYPE_LABELS: Record<PlanTaskType, string> = {
  PHASE: 'Phase',
  SUMMARY_TASK: 'Nhóm tóm tắt',
  WORK_PACKAGE: 'Work Package',
  TASK: 'Task',
  MILESTONE: 'Milestone',
  EXTERNAL_TASK: 'Task ngoài'
};

export const PLAN_TASK_STATUS_LABELS: Record<PlanTaskStatus, string> = {
  NOT_STARTED: 'Chưa bắt đầu',
  IN_PROGRESS: 'Đang làm',
  COMPLETED: 'Hoàn thành',
  DELAYED: 'Trễ hạn',
  CANCELLED: 'Đã hủy'
};

export const TASK_PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Thấp',
  MEDIUM: 'Trung bình',
  HIGH: 'Cao',
  CRITICAL: 'Khẩn cấp'
};

export const SCHEDULE_MODE_LABELS: Record<ScheduleMode, string> = {
  AUTO: 'Tự động',
  MANUAL: 'Thủ công'
};

export const CONSTRAINT_TYPE_LABELS: Record<ConstraintType, string> = {
  FIXED_DATE: 'Ngày cố định',
  START_NO_EARLIER_THAN: 'Không sớm hơn',
  START_NO_LATER_THAN: 'Không trễ hơn',
  REMOVE_SCHEDULE: 'Bỏ lịch'
};

// ─── Gantt (docs/api/13 §3.3, docs/planning/13) ───────────────────
export interface GanttPlanBrief {
  id: string;
  planCode: string;
  planName: string;
  planType: PlanType;
  status: PlanStatus;
}

export interface GanttBaseline {
  start?: string;
  finish?: string;
}

export interface GanttResource {
  resourceId: string;
  resourceType: string;
  allocationPercent: number;
}

export interface GanttTask {
  id: string;
  parentId?: string;
  wbsCode: string;
  taskName: string;
  taskType: PlanTaskType;
  start?: string;
  finish?: string;
  durationMinutes?: number;
  plannedEffortMinutes?: number;
  percentComplete: number;
  status: PlanTaskStatus;
  scheduleMode: ScheduleMode;
  isCritical: boolean;
  baseline?: GanttBaseline;
  resources: GanttResource[];
}

export interface GanttDependency {
  from: string;
  to: string;
  type: DependencyType;
  lagMinutes: number;
}

export interface GanttData {
  plan: GanttPlanBrief;
  tasks: GanttTask[];
  dependencies: GanttDependency[];
  warnings: string[];
}

export const GANTT_ZOOM_LABELS: Record<string, string> = {
  DAY: 'Ngày',
  WEEK: 'Tuần',
  MONTH: 'Tháng'
};

export type GanttZoom = 'DAY' | 'WEEK' | 'MONTH';