// Matches backend-makeover.md §1 DeploymentEvent entity.
export type DeploymentEventType =
  | "DEPLOY_TRIGGERED"
  | "PULL_STARTED"
  | "PULL_FINISHED"
  | "CONTAINER_CREATING"
  | "CONTAINER_STARTED"
  | "HEALTH_OK"
  | "BUILD_STARTED"
  | "BUILD_FINISHED"
  | "DEPLOY_STARTED"
  | "DEPLOY_FINISHED"
  | "FAILED"
  | "CRASHED"
  | "RESTARTED"
  | "STOPPED"
  | "MANUAL_ROLLBACK"
  | "WEBHOOK_IGNORED"
  | "PIN_RELEASED"
  | "UPDATE_AVAILABLE"
  | "UPDATE_TRIGGERED"
  | "UPDATE_SUCCESS"
  | "UPDATE_FAILED"
  | "UPDATER_UNREACHABLE"
  | "SELF_APP_BOOTSTRAPPED";

export type ProgressStage =
  | "PULL_LAYER"
  | "CONTAINER_CREATE"
  | "CONTAINER_START"
  | "HEALTH_PROBE"
  | "UPDATER_RECREATE"
  | "UPDATER_BOOT";

export interface ProgressFrame {
  operationId: string;
  stage: ProgressStage;
  message: string;
  current: number | null;
  total: number | null;
  unit: string | null;
  emittedAt: string; // ISO 8601
}

export type DeploymentEventStatus = "SUCCESS" | "FAILURE" | "IN_PROGRESS";

export type TriggerSource = "AUTOMATIC" | "MANUAL" | "ROLLBACK" | "RESTART" | "SELF_UPDATE";

export interface DeploymentEvent {
  id: number;
  appName: string;
  operationId: string | null; // nullable for legacy rows
  eventType: DeploymentEventType;
  status: DeploymentEventStatus;
  imageName: string | null;
  branch: string | null;
  commitSha: string | null;
  commitMessage: string | null;
  commitAuthor: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  triggeredBy: TriggerSource;
  rollbackFromSha: string | null;
  createdAt: string; // ISO 8601
  finishedAt: string | null;
  availableLocally?: boolean | null;
}

export interface DeploymentEventPayload {
  imageName: string | null;
  branch: string | null;
  commitSha: string | null;
  commitMessage: string | null;
  commitAuthor: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  triggeredBy: TriggerSource | null;
  rollbackFromSha: string | null;
  availableLocally: boolean | null;
}

export interface PlatformEventEnvelope {
  version: 1;
  id: number;
  operationId: string | null;
  appName: string;
  type: DeploymentEventType;
  status: DeploymentEventStatus;
  occurredAt: string;
  payload: DeploymentEventPayload;
}

// Matches backend-makeover.md §2
export interface ContainerStats {
  cpuPercent: number;
  memoryUsedMB: number;
  memoryLimitMB: number;
  uptimeSeconds: number;
  restartCount: number;
}

// Matches backend-makeover.md §3
export interface CommitInfo {
  sha: string;
  message: string;
  author: string;
  date: string;
  url: string;
}

export interface CommitsAhead {
  count: number | null; // null = no GitHub PAT configured
  commits: CommitInfo[];
  compareUrl: string;
}

// Matches backend-makeover.md §5 preferences jsonb shape
export interface UserPreferences {
  notify_on_fail: boolean;
  notify_on_first_deploy: boolean;
  notify_on_crash: boolean;
  notify_on_rollback: boolean;
  notify_on_update_available: boolean;
  layout_mode: "grid" | "list";
  reduced_motion: "system" | "always" | "never";
  pinned_apps: string[];
}

export interface UserAccount {
  id: number;
  email: string;
  preferences: UserPreferences;
  createdAt: string;
}

// Matches backend-makeover.md §9
export interface SetupStatus {
  secretConfigured: boolean;
  dockerReachable: boolean;
  githubTokenConfigured: boolean;
}
