export interface ProjectDodItemResponse {
  id: string;
  projectId: string;
  text: string;
  displayOrder: number;
  active: boolean;
  version: number;
}

export interface ProjectDodItemCreateRequest {
  text: string;
}

export interface ProjectDodItemUpdateRequest {
  text: string;
  active: boolean;
  version: number;
}
