export interface EvmSnapshotResponse {
  id: string;
  snapshotDate: string;
  plannedValue: number;
  earnedValue: number;
  actualCost: number;
  costVariance: number;
  scheduleVariance: number;
  cpi: number;
  spi: number;
}

export interface ProjectMemberFinanceResponse {
  memberId: string;
  userId: string;
  username: string;
  fullName: string;
  role: string;
  hourlyRate?: number;
}
