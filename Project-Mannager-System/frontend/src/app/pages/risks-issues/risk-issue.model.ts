export type RiskStatus = 'OPEN' | 'MONITORING' | 'OCCURRED' | 'RESOLVED' | 'CLOSED';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface UserBriefRef {
  id: string;
  fullName: string;
  username?: string;
}

export interface RiskResponse {
  id: string;
  code: string;
  projectId: string;
  projectCode?: string;
  projectName?: string;
  title: string;
  description?: string;
  probability: string;
  impact: string;
  level: RiskLevel;
  owner?: UserBriefRef;
  mitigationPlan?: string;
  contingencyPlan?: string;
  status: RiskStatus;
  dueDate?: string;
  linkedIssueId?: string;
  createdAt: string;
}

export interface RiskCreateRequest {
  projectId: string;
  title: string;
  description?: string;
  probability: string;
  impact: string;
  level?: string;
  ownerId: string;
  mitigationPlan?: string;
  contingencyPlan?: string;
  status?: RiskStatus;
  dueDate?: string;
}

export interface RiskUpdateRequest extends RiskCreateRequest {
  status: RiskStatus;
}

export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type IssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface IssueResponse {
  id: string;
  code: string;
  projectId: string;
  projectCode?: string;
  projectName?: string;
  title: string;
  description?: string;
  severity: IssueSeverity;
  owner?: UserBriefRef;
  rootCause?: string;
  solution?: string;
  status: IssueStatus;
  dueDate?: string;
  resolvedAt?: string;
  createdAt: string;
}

export interface IssueCreateRequest {
  projectId: string;
  title: string;
  description?: string;
  severity: IssueSeverity;
  ownerId: string;
  rootCause?: string;
  solution?: string;
  status?: IssueStatus;
  dueDate?: string;
}

export interface IssueUpdateRequest extends IssueCreateRequest {
  status: IssueStatus;
}
