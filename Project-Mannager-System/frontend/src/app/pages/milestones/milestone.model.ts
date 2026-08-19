export type MilestoneStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'DELAYED' | 'CANCELLED';

export interface MilestoneResponse {
  id: string;
  projectId: string;
  projectCode?: string;
  projectName?: string;
  name: string;
  description?: string;
  plannedDate: string;
  actualDate?: string;
  status: MilestoneStatus;
  progress: number;
  note?: string;
  createdAt: string;
  version: number;
}

export interface MilestoneCreateRequest {
  projectId: string;
  name: string;
  description?: string;
  plannedDate: string;
  note?: string;
}

export interface MilestoneUpdateRequest {
  name: string;
  description?: string;
  plannedDate: string;
  actualDate?: string;
  status: MilestoneStatus;
  progress: number;
  note?: string;
  version: number;
}
