export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE' | 'BLOCKED' | 'CANCELLED';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT' | 'CRITICAL';

export type TaskType = 'FEATURE' | 'BUG' | 'TASK' | 'IMPROVEMENT' | 'OTHER';

export type TaskSource = 'MANUAL' | 'MEETING' | 'ACTION_ITEM' | 'ISSUE' | 'OTHER';

export interface UserBriefRef {
  id: string;
  fullName: string;
  username?: string;
}

export interface TagRef {
  id: string;
  name: string;
  color?: string;
}

export interface TaskSummaryResponse {
  id: string;
  code: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  type: TaskType;
  source?: TaskSource;
  assigneeId?: string;
  assigneeName?: string;
  progress: number;
  blocked: boolean;
  startDate?: string;
  dueDate?: string;
  actualCompletedAt?: string;
  blockerReason?: string;
  commentCount: number;
  attachmentCount: number;
  createdAt: string;
  updatedAt: string;
  sprintId?: string;
  assignee?: UserBriefRef;
  version: number;
}

export interface TaskDetailResponse extends TaskSummaryResponse {
  description?: string;
  notes?: string;
  estimateMinutes?: number;
  parentTaskId?: string;
  parentTaskCode?: string;
  reporter?: UserBriefRef;
  assignee?: UserBriefRef;
  assigneeId?: string;
  assigneeName?: string;
  collaborators: UserBriefRef[];
  watchers: UserBriefRef[];
  tags: TagRef[];
  version: number;
}

export interface TaskCreateRequest {
  projectId: string;
  parentTaskId?: string;
  title: string;
  description?: string;
  type?: TaskType;
  source?: TaskSource;
  priority?: TaskPriority;
  assigneeId?: string;
  collaboratorIds?: string[];
  watcherIds?: string[];
  tagIds?: string[];
  status?: TaskStatus;
  startDate?: string;
  dueDate?: string;
  progress?: number;
  blocked?: boolean;
  blockerReason?: string;
  estimateMinutes?: number;
  notes?: string;
}

export interface TaskUpdateRequest {
  title: string;
  description?: string;
  notes?: string;
  type?: TaskType;
  priority?: TaskPriority;
  assigneeId?: string;
  collaboratorIds?: string[];
  watcherIds?: string[];
  tagIds?: string[];
  status?: TaskStatus;
  startDate?: string;
  dueDate?: string;
  progress?: number;
  blocked?: boolean;
  blockerReason?: string;
  estimateMinutes?: number;
  sprintId?: string;
  version: number;
}

export interface TaskStatusRequest {
  status: TaskStatus;
  blockerReason?: string;
}

export interface TaskProgressRequest {
  progress: number;
}

export interface TaskBlockerRequest {
  blocked: boolean;
  blockerReason?: string;
}

export interface TaskAssigneeRequest {
  assigneeId: string | null;
}

export interface TaskTagsRequest {
  tagIds: string[];
}

export interface TaskCollaboratorsRequest {
  userIds: string[];
}

export interface TaskWatchersRequest {
  userIds: string[];
}

export interface TaskComment {
  id: string;
  taskId: string;
  userId: string;
  userFullName: string;
  username?: string;
  content: string;
  createdAt: string;
  updatedAt?: string;
}

export interface TaskAttachment {
  id: string;
  taskId?: string;
  fileName: string;
  filePath?: string;
  fileSize?: number;
  sizeBytes?: number;
  contentType: string;
  fileUrl?: string;
  uploadedBy?: string;
  uploadedByName?: string;
  uploadedAt?: string;
  createdAt?: string;
}

export interface TaskHistoryChange {
  from: unknown;
  to: unknown;
}

export interface TaskHistoryEntry {
  changedAt: string;
  changedBy?: string;
  changedByUsername?: string;
  changedByFullName?: string;
  action: string;
  changes?: Record<string, TaskHistoryChange>;
  // legacy fields for backward compat
  id?: string;
  taskId?: string;
  userId?: string;
  userFullName?: string;
  fieldName?: string;
  oldValue?: string;
  newValue?: string;
  createdAt?: string;
}

export interface TaskFilterParams {
  keyword?: string;
  projectId?: string;
  assigneeId?: string;
  status?: string[];
  priority?: string[];
  type?: string[];
  overdue?: boolean;
  blocked?: boolean;
  dueDateFrom?: string;
  dueDateTo?: string;
  sprintId?: string;
  page?: number;
  size?: number;
  sort?: string;
}
