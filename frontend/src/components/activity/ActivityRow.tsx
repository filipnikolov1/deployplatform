import {
  Circle,
  CheckCircle2,
  XCircle,
  Clock,
  RotateCcw,
  Ban,
  type LucideIcon,
} from "lucide-react";
import type {
  DeploymentEvent,
  DeploymentEventType,
} from "@/types/launchpad";

interface Props {
  event: DeploymentEvent;
  isLast: boolean;
}

interface EventTypeEntry {
  title: string;
  color: string;
  icon: LucideIcon;
  label: string;
}

const eventTypeConfig: Record<DeploymentEventType, EventTypeEntry> = {
  DEPLOY_FINISHED: {
    title: "Deploy completed",
    color: "bg-emerald-500",
    icon: CheckCircle2,
    label: "Success",
  },
  DEPLOY_TRIGGERED: {
    title: "Deploy triggered",
    color: "bg-blue-500",
    icon: Circle,
    label: "Running",
  },
  DEPLOY_STARTED: {
    title: "Deploy in progress",
    color: "bg-blue-500",
    icon: Circle,
    label: "Running",
  },
  BUILD_STARTED: {
    title: "Build started",
    color: "bg-amber-500",
    icon: Clock,
    label: "Building",
  },
  BUILD_FINISHED: {
    title: "Build completed",
    color: "bg-emerald-500",
    icon: CheckCircle2,
    label: "Success",
  },
  FAILED: {
    title: "Deploy failed",
    color: "bg-red-500",
    icon: XCircle,
    label: "Failed",
  },
  CRASHED: {
    title: "Container crashed",
    color: "bg-red-500",
    icon: XCircle,
    label: "Crashed",
  },
  RESTARTED: {
    title: "Container restarted",
    color: "bg-blue-500",
    icon: RotateCcw,
    label: "Restarted",
  },
  STOPPED: {
    title: "Container stopped",
    color: "bg-slate-500",
    icon: Circle,
    label: "Stopped",
  },
  MANUAL_ROLLBACK: {
    title: "Rolled back",
    color: "bg-amber-500",
    icon: RotateCcw,
    label: "Rollback",
  },
  WEBHOOK_IGNORED: {
    title: "Webhook ignored",
    color: "bg-slate-500",
    icon: Ban,
    label: "Ignored",
  },
  PIN_RELEASED: {
    title: "Pin released",
    color: "bg-blue-500",
    icon: CheckCircle2,
    label: "Unpinned",
  },
};

function relative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hour${hours > 1 ? "s" : ""} ago`;
  const days = Math.floor(hours / 24);
  return `${days} day${days > 1 ? "s" : ""} ago`;
}

export function ActivityRow({ event, isLast }: Props) {
  const config = eventTypeConfig[event.eventType];
  const Icon = config.icon;

  return (
    <div className="relative group hover:bg-white/5 transition-colors">
      <div className="flex gap-4 p-6">
        <div className="relative flex flex-col items-center">
          <div
            className={`w-2.5 h-2.5 rounded-full ${config.color} ring-4 ring-black z-10`}
          >
            <span className="sr-only">{config.label}</span>
          </div>
          {!isLast && (
            <div className="absolute top-2.5 w-px h-full bg-white/10" />
          )}
        </div>
        <div className="flex-1 min-w-0 pt-0.5">
          <div className="flex items-start justify-between gap-4 mb-1">
            <div className="flex items-center gap-3 flex-wrap">
              <h3 className="font-medium text-white">{config.title}</h3>
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-white/10 text-xs font-medium text-slate-200">
                <Icon className="w-3 h-3" />
                {config.label}
              </span>
              {event.triggeredBy === "MANUAL" && (
                <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-300 text-xs font-medium border border-purple-500/20">
                  manual
                </span>
              )}
            </div>
            <time
              className="text-sm text-slate-400 whitespace-nowrap tabular-nums"
              dateTime={event.createdAt}
            >
              {relative(event.createdAt)}
            </time>
          </div>
          {event.commitMessage && (
            <p className="text-sm text-slate-400 mb-1 line-clamp-1">
              {event.commitMessage}
            </p>
          )}
          {event.errorMessage && (
            <p className="text-sm text-red-400/80 mb-1 line-clamp-1">
              {event.errorMessage}
            </p>
          )}
          <div className="flex items-center gap-3 text-sm text-slate-400">
            <span className="font-mono">{event.appName}</span>
            {event.commitSha && (
              <span className="font-mono text-xs px-1.5 py-0.5 bg-white/5 rounded">
                {event.commitSha.slice(0, 7)}
              </span>
            )}
            {event.durationMs != null && (
              <span className="text-xs tabular-nums">
                {(event.durationMs / 1000).toFixed(1)}s
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
