export type TimelineEventType = "DEPLOY" | "CRASH" | "RESTART" | "COMMIT";

export interface TimelineEvent {
  id: number;
  appName: string;
  eventType: TimelineEventType;
  commitSha: string;
  occurredAt: string;
  metadata: string;
  sourceEventId: number | null;
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

export type EvidenceType = "log" | "diff" | "commit";

export interface EvidenceItem {
  id: number;
  type: EvidenceType;
  source?: string;
  timestamp?: string | null;
  content: unknown;
  repoSlug?: string;
  suspectSha?: string;
}

export interface CrashSignals {
  timeSinceDeployMinutes: number | null;
  crashCountForCommit: number;
  crashedAt: string;
  lastDeployedAt: string | null;
}

export interface CrashAnalysis {
  id: number;
  appName: string;
  crashEventId: number;
  suspectCommitSha: string | null;
  lastGoodCommitSha: string | null;
  suspectFilePath: string | null;
  suspectLine: number | null;
  aiNarration: string | null;
  aiProviderUsed: string | null;
  evidence: EvidenceItem[];
  signals: CrashSignals;
  generatedAt: string | null;
}
