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
}

export const statusConfig: Record<AppStatus, StatusConfigEntry> = {
  RUNNING: {
    icon: CheckCircle2,
    iconColor: "text-green-400",
    bgColor: "bg-green-500/10",
    borderColor: "border-green-500/20",
    label: "Running",
    dotColor: "bg-green-500",
  },
  BUILDING: {
    icon: Activity,
    iconColor: "text-amber-400",
    bgColor: "bg-amber-500/10",
    borderColor: "border-amber-500/20",
    label: "Building",
    dotColor: "bg-amber-500",
  },
  FAILED: {
    icon: XCircle,
    iconColor: "text-red-400",
    bgColor: "bg-red-500/10",
    borderColor: "border-red-500/20",
    label: "Failed",
    dotColor: "bg-red-500",
  },
  CRASHED: {
    icon: XCircle,
    iconColor: "text-red-400",
    bgColor: "bg-red-500/10",
    borderColor: "border-red-500/20",
    label: "Crashed",
    dotColor: "bg-red-500",
  },
  STOPPED: {
    icon: Clock,
    iconColor: "text-slate-400",
    bgColor: "bg-slate-500/10",
    borderColor: "border-slate-500/20",
    label: "Stopped",
    dotColor: "bg-slate-500",
  },
  PENDING: {
    icon: Clock,
    iconColor: "text-slate-400",
    bgColor: "bg-slate-500/10",
    borderColor: "border-slate-500/20",
    label: "Pending",
    dotColor: "bg-slate-500",
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
