export type DeploymentStatus =
  | "PENDING"
  | "RUNNING"
  | "STOPPED"
  | "FAILED"
  | "DOWN";

export interface Deployment {
  id: number;
  appName: string;
  repoUrl: string;
  imageName: string;
  containerPort: number;
  status: DeploymentStatus;
  branch?: string;
  commitSha?: string | null;
  commitMessage?: string | null;
  createdAt: string;
  updatedAt: string;
  isSelfApp?: boolean;
  latestKnownImage?: string | null;
  latestKnownSha?: string | null;
  latestKnownMessage?: string | null;
}
