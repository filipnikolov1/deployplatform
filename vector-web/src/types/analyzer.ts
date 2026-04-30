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
