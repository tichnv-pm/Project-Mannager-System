export type MeetingStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface UserBriefRef {
  id: string;
  fullName: string;
  username?: string;
  email?: string;
}

export interface MeetingAttachment {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  filePath?: string;
  uploadedByName?: string;
  createdAt: string;
}

export interface ActionItemResponse {
  id: string;
  meetingId: string;
  projectId: string;
  title: string;
  description?: string;
  assignee?: UserBriefRef;
  assigneeId?: string;
  dueDate?: string;
  priority: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';
  progress: number;
  linkedTaskId?: string;
  createdAt: string;
  version: number;
}

export interface MeetingResponse {
  id: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  title: string;
  startTime: string;
  endTime: string;
  location?: string;
  meetingLink?: string;
  chairpersonId: string;
  chairpersonName: string;
  chairperson?: UserBriefRef;
  participants?: UserBriefRef[];
  participantCount: number;
  agenda?: string;
  content?: string;
  conclusion?: string;
  minutes?: string;            // legacy field alias
  status: MeetingStatus;
  attachmentCount?: number;
  actionItemCount?: number;
  actionItems?: ActionItemResponse[];
  createdAt: string;
  version: number;
}

export interface MeetingCreateRequest {
  projectId: string;
  title: string;
  startTime: string;
  endTime: string;
  location?: string;
  meetingLink?: string;
  chairpersonId: string;
  participantIds?: string[];
  agenda?: string;
  status?: MeetingStatus;
}

export interface MeetingUpdateRequest {
  title: string;
  startTime: string;
  endTime: string;
  location?: string;
  meetingLink?: string;
  chairpersonId: string;
  participantIds?: string[];
  agenda?: string;
  status?: MeetingStatus;
  version: number;
}

export interface MeetingCompleteRequest {
  content?: string;
  conclusion: string;
}

export interface ActionItemCreateRequest {
  meetingId: string;
  projectId: string;
  title: string;
  description?: string;
  assigneeId: string;
  dueDate?: string;
  priority?: string;
}

export interface ActionItemUpdateRequest {
  title?: string;
  description?: string;
  assigneeId?: string;
  dueDate?: string;
  priority?: string;
  status?: string;
  progress?: number;
  version: number;
}
