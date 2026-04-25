import type { DeploymentStatus } from "@/types/deployment";

export interface StatusTokens {
  color: string;
  label: string;
}

export const STATUS_TOKENS: Record<DeploymentStatus, StatusTokens> = {
  RUNNING: { color: "#22C55E", label: "Running" },
  FAILED: { color: "#EF4444", label: "Failed" },
  STOPPED: { color: "#F59E0B", label: "Stopped" },
  PENDING: { color: "#64748B", label: "Pending" },
  DOWN: { color: "#64748B", label: "Down" },
};

export function statusTokens(status: DeploymentStatus): StatusTokens {
  return STATUS_TOKENS[status];
}
