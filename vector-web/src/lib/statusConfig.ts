import {
  Activity,
  CheckCircle2,
  Clock,
  type LucideIcon,
  XCircle,
} from "lucide-react";
import type { DeploymentStatus } from "@/types/deployment";

export type AppStatus =
  | "RUNNING"
  | "BUILDING"
  | "FAILED"
  | "CRASHED"
  | "STOPPED"
  | "PENDING";

export interface StatusConfigEntry {
  icon: LucideIcon;
  iconColor: string;
  bgColor: string;
  borderColor: string;
  label: string;
  dotColor: string;
  fg: string;
  bg: string;
  line: string;
}

export const statusConfig: Record<AppStatus, StatusConfigEntry> = {
  RUNNING: {
    icon: CheckCircle2,
    iconColor: "text-status-running-fg",
    bgColor: "bg-status-running-bg",
    borderColor: "border-status-running-line",
    label: "Running",
    dotColor: "var(--c-status-running)",
    fg: "var(--c-status-running-fg)",
    bg: "var(--c-status-running-bg)",
    line: "var(--c-status-running-line)",
  },
  BUILDING: {
    icon: Activity,
    iconColor: "text-status-building-fg",
    bgColor: "bg-status-building-bg",
    borderColor: "border-status-building-line",
    label: "Building",
    dotColor: "var(--c-status-building)",
    fg: "var(--c-status-building-fg)",
    bg: "var(--c-status-building-bg)",
    line: "var(--c-status-building-line)",
  },
  FAILED: {
    icon: XCircle,
    iconColor: "text-status-failed-fg",
    bgColor: "bg-status-failed-bg",
    borderColor: "border-status-failed-line",
    label: "Failed",
    dotColor: "var(--c-status-failed)",
    fg: "var(--c-status-failed-fg)",
    bg: "var(--c-status-failed-bg)",
    line: "var(--c-status-failed-line)",
  },
  CRASHED: {
    icon: XCircle,
    iconColor: "text-status-failed-fg",
    bgColor: "bg-status-failed-bg",
    borderColor: "border-status-failed-line",
    label: "Crashed",
    dotColor: "var(--c-status-failed)",
    fg: "var(--c-status-failed-fg)",
    bg: "var(--c-status-failed-bg)",
    line: "var(--c-status-failed-line)",
  },
  STOPPED: {
    icon: Clock,
    iconColor: "text-status-stopped-fg",
    bgColor: "bg-status-stopped-bg",
    borderColor: "border-status-stopped-line",
    label: "Stopped",
    dotColor: "var(--c-status-stopped)",
    fg: "var(--c-status-stopped-fg)",
    bg: "var(--c-status-stopped-bg)",
    line: "var(--c-status-stopped-line)",
  },
  PENDING: {
    icon: Clock,
    iconColor: "text-status-stopped-fg",
    bgColor: "bg-status-stopped-bg",
    borderColor: "border-status-stopped-line",
    label: "Pending",
    dotColor: "var(--c-status-stopped)",
    fg: "var(--c-status-stopped-fg)",
    bg: "var(--c-status-stopped-bg)",
    line: "var(--c-status-stopped-line)",
  },
};

export function toAppStatus(status: DeploymentStatus): AppStatus {
  switch (status) {
    case "RUNNING":
      return "RUNNING";
    case "PENDING":
      return "BUILDING";
    case "FAILED":
      return "FAILED";
    case "DOWN":
      return "CRASHED";
    case "STOPPED":
    default:
      return "STOPPED";
  }
}
