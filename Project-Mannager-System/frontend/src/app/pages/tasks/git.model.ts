export interface GitCommitResponse {
  id: string;
  commitHash: string;
  message: string;
  author: string;
  commitUrl?: string;
  createdAt: string;
}

export interface GitPullRequestResponse {
  id: string;
  prNumber: number;
  title: string;
  status: string;
  prUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GitInfoResponse {
  commits: GitCommitResponse[];
  pullRequests: GitPullRequestResponse[];
}
