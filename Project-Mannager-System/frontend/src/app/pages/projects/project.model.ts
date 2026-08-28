export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';

export type ProjectMemberRole = 'PROJECT_MANAGER' | 'TECH_LEAD' | 'DEVELOPER' | 'TESTER' | 'BUSINESS_ANALYST' | 'DEVOPS' | 'MEMBER' | 'DEV' | 'BA' | 'PROJECT_MEMBER';

export interface ProjectResponse {
  id: string;
  code: string;
  name: string;
  description?: string;
  status: ProjectStatus;
  startDate?: string;
  endDate?: string;
  customerName?: string;
  projectManagerId?: string;
  projectManagerName?: string;
  progress: number;
  memberCount: number;
  createdAt: string;
  version: number;
}

export interface ProjectCreateRequest {
  code: string;
  name: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  customerName?: string;
  projectManagerId?: string;
  note?: string;
}

export interface ProjectUpdateRequest {
  code: string;
  name: string;
  description?: string;
  status: ProjectStatus;
  startDate?: string;
  endDate?: string;
  customerName?: string;
  progress?: number;
  projectManagerId?: string;
  note?: string;
  version: number;
}

export interface ProjectMemberResponse {
  userId: string;
  username: string;
  fullName: string;
  email: string;
  role: ProjectMemberRole;
  joinedAt: string;
}

export interface ProjectMemberRequest {
  userId: string;
  role: ProjectMemberRole;
}
