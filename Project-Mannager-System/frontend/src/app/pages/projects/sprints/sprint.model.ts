export type SprintStatus = 'FUTURE' | 'ACTIVE' | 'COMPLETED';

export interface SprintResponse {
  id: string;
  projectId: string;
  sprintName: string;
  startDate: string;
  endDate: string;
  status: SprintStatus;
  goal?: string;
}

export interface SprintCreateRequest {
  sprintName: string;
  startDate: string;
  endDate: string;
  goal?: string;
}

export interface SprintUpdateRequest {
  sprintName: string;
  startDate: string;
  endDate: string;
  status: SprintStatus;
  goal?: string;
}
