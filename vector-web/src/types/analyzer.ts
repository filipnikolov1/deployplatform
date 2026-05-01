export type TimelineEventType = "DEPLOY" | "CRASH" | "RESTART" | "COMMIT";

export interface TimelineEvent {
  id: number;
  appName: string;
  eventType: TimelineEventType;
  commitSha: string;
  occurredAt: string;
  metadata: string;
}

export interface AppStats {
  appName: string;
  deploys30d: number;
  crashes30d: number;
  restarts30d: number;
  commits30d: number;
  lastDeployedAt: string | null;
}

export interface RecentDeploy {
  id: number;
  commitSha: string;
  occurredAt: string;
  metadata: string;
}

export interface CommitDetail {
  sha: string;
  message: string;
  author: string;
  authoredAt: string | null;
  deploymentIds: number[];
  repoSlug: string | null;
}

export interface LogEntry {
  id: number;
  timestamp: string;
  stream: "stdout" | "stderr";
  line: string;
}

export interface CommitDiff {
  available: boolean;
  diffJson?: string;
  reason?: string;
}

export interface CommitFile {
  available: boolean;
  content?: string;
  name?: string;
  path?: string;
}
