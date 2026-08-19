export interface DashboardSummaryResponse {
  totalTasksToday: number;
  overdueTasks: number;
  upcomingTasks: number;
  inProgressTasks: number;
  blockedTasks: number;
  meetingsToday: number;
  pendingActionItems: number;
  highRisks: number;
  openIssues: number;
  upcomingMilestones: number;
}

export interface StatusStat {
  status: string;
  count: number;
}

export interface PriorityStat {
  priority: string;
  count: number;
}

export interface TaskStatsResponse {
  tasksByStatus: StatusStat[];
  tasksByPriority: PriorityStat[];
}

export interface ProjectProgressItem {
  projectId: string;
  code: string;
  name: string;
  progress: number;
}

export interface ProjectProgressResponse {
  projects: ProjectProgressItem[];
}

export interface ProjectOption {
  id: string;
  code: string;
  name: string;
}
