import type { DeploymentStatus } from "@/types/deployment";

export interface StatusTokens {
  color: string;
  label: string;
}

export const STATUS_TOKENS: Record<DeploymentStatus, StatusTokens> = {
  RUNNING: { color: "var(--c-status-running)", label: "Running" },
  FAILED:  { color: "var(--c-status-failed)",  label: "Failed" },
  STOPPED: { color: "var(--c-status-building)", label: "Stopped" },
  PENDING: { color: "var(--c-status-stopped)",  label: "Pending" },
  DOWN:    { color: "var(--c-status-stopped)",  label: "Down" },
};

export function statusTokens(status: DeploymentStatus): StatusTokens {
  return STATUS_TOKENS[status];
}
