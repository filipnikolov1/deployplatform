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
  createdAt: string;
  updatedAt: string;
}
