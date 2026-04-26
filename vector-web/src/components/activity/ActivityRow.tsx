import {
  Circle,
  CheckCircle2,
  XCircle,
  Clock,
  RotateCcw,
  Ban,
  ArrowUpCircle,
  Sparkles,
  AlertTriangle,
  type LucideIcon,
} from "lucide-react";
import type {
  DeploymentEvent,
  DeploymentEventType,
} from "@/types/vector";

interface Props {
  event: DeploymentEvent;
  isLast: boolean;
}

interface EventTypeEntry {
  title: string;
  chipClass: string;
  badgeClass: string;
  icon: LucideIcon;
  label: string;
}

const eventTypeConfig: Record<DeploymentEventType, EventTypeEntry> = {
  DEPLOY_FINISHED: {
    title: "Deploy completed",
    chipClass: "border-emerald-400/30 bg-emerald-500/14 text-emerald-200",
    badgeClass: "border-emerald-400/20 bg-emerald-500/10 text-emerald-200",
    icon: CheckCircle2,
    label: "Success",
  },
  DEPLOY_TRIGGERED: {
    title: "Deploy triggered",
    chipClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    badgeClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    icon: Circle,
    label: "Running",
  },
  DEPLOY_STARTED: {
    title: "Deploy in progress",
    chipClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    badgeClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    icon: Circle,
    label: "Running",
  },
  BUILD_STARTED: {
    title: "Build started",
    chipClass: "border-amber-300/25 bg-amber-500/10 text-amber-100",
    badgeClass: "border-amber-300/20 bg-amber-500/10 text-amber-100",
    icon: Clock,
    label: "Building",
  },
  BUILD_FINISHED: {
    title: "Build completed",
    chipClass: "border-emerald-400/30 bg-emerald-500/14 text-emerald-200",
    badgeClass: "border-emerald-400/20 bg-emerald-500/10 text-emerald-200",
    icon: CheckCircle2,
    label: "Success",
  },
  FAILED: {
    title: "Deploy failed",
    chipClass: "border-red-400/25 bg-red-500/12 text-red-200",
    badgeClass: "border-red-400/20 bg-red-500/10 text-red-200",
    icon: XCircle,
    label: "Failed",
  },
  CRASHED: {
    title: "Container crashed",
    chipClass: "border-red-400/25 bg-red-500/12 text-red-200",
    badgeClass: "border-red-400/20 bg-red-500/10 text-red-200",
    icon: XCircle,
    label: "Crashed",
  },
  RESTARTED: {
    title: "Container restarted",
    chipClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    badgeClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    icon: RotateCcw,
    label: "Restarted",
  },
  STOPPED: {
    title: "Container stopped",
    chipClass: "border-white/10 bg-white/[0.06] text-slate-200",
    badgeClass: "border-white/10 bg-white/[0.06] text-slate-200",
    icon: Circle,
    label: "Stopped",
  },
  MANUAL_ROLLBACK: {
    title: "Rolled back",
    chipClass: "border-amber-300/25 bg-amber-500/10 text-amber-100",
    badgeClass: "border-amber-300/20 bg-amber-500/10 text-amber-100",
    icon: RotateCcw,
    label: "Rollback",
  },
  WEBHOOK_IGNORED: {
    title: "Webhook ignored",
    chipClass: "border-white/10 bg-white/[0.06] text-slate-300",
    badgeClass: "border-white/10 bg-white/[0.06] text-slate-300",
    icon: Ban,
    label: "Ignored",
  },
  PIN_RELEASED: {
    title: "Pin released",
    chipClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    badgeClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    icon: CheckCircle2,
    label: "Unpinned",
  },
  UPDATE_AVAILABLE: {
    title: "Update available",
    chipClass: "border-sky-300/25 bg-sky-400/12 text-sky-100",
    badgeClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    icon: ArrowUpCircle,
    label: "Available",
  },
  UPDATE_TRIGGERED: {
    title: "Self-update triggered",
    chipClass: "border-sky-300/25 bg-sky-400/12 text-sky-100",
    badgeClass: "border-sky-300/20 bg-sky-400/10 text-sky-100",
    icon: ArrowUpCircle,
    label: "Updating",
  },
  UPDATE_SUCCESS: {
    title: "Self-update succeeded",
    chipClass: "border-emerald-400/30 bg-emerald-500/14 text-emerald-200",
    badgeClass: "border-emerald-400/20 bg-emerald-500/10 text-emerald-200",
    icon: CheckCircle2,
    label: "Updated",
  },
  UPDATE_FAILED: {
    title: "Self-update failed",
    chipClass: "border-red-400/25 bg-red-500/12 text-red-200",
    badgeClass: "border-red-400/20 bg-red-500/10 text-red-200",
    icon: XCircle,
    label: "Failed",
  },
  UPDATER_UNREACHABLE: {
    title: "Updater unreachable",
    chipClass: "border-amber-300/25 bg-amber-500/10 text-amber-100",
    badgeClass: "border-amber-300/20 bg-amber-500/10 text-amber-100",
    icon: AlertTriangle,
    label: "Unreachable",
  },
  SELF_APP_BOOTSTRAPPED: {
    title: "Self-app bootstrapped",
    chipClass: "border-white/10 bg-white/[0.06] text-slate-200",
    badgeClass: "border-white/10 bg-white/[0.06] text-slate-200",
    icon: Sparkles,
    label: "Bootstrapped",
  },
};

const fallbackEventConfig: EventTypeEntry = {
  title: "Event",
  chipClass: "border-white/10 bg-white/[0.06] text-slate-300",
  badgeClass: "border-white/10 bg-white/[0.06] text-slate-300",
  icon: Circle,
  label: "Event",
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
  const config = eventTypeConfig[event.eventType] ?? fallbackEventConfig;
  const Icon = config.icon;

  return (
    <div className="group relative transition-colors hover:bg-white/[0.03]">
      <div className="flex gap-4 px-5 py-5 sm:px-6">
        <div className="relative flex w-10 shrink-0 justify-center">
          <div
            className={`relative z-10 flex h-10 w-10 items-center justify-center rounded-2xl border backdrop-blur-xl ${config.chipClass}`}
          >
            <Icon className="h-4 w-4" />
            <span className="sr-only">{config.label}</span>
          </div>
          {!isLast && (
            <div className="absolute bottom-[-20px] top-10 w-px bg-gradient-to-b from-white/12 to-transparent" />
          )}
        </div>
        <div className="min-w-0 flex-1 pt-1">
          <div className="mb-1 flex items-start justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              <h3 className="font-medium text-white">{config.title}</h3>
              <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.16em] ${config.badgeClass}`}>
                <Icon className="w-3 h-3" />
                {config.label}
              </span>
              {event.triggeredBy === "MANUAL" && (
                <span className="inline-flex items-center rounded-full border border-white/10 bg-white/[0.05] px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.16em] text-slate-300">
                  manual
                </span>
              )}
            </div>
            <time
              className="whitespace-nowrap text-sm tabular-nums text-slate-500"
              dateTime={event.createdAt}
            >
              {relative(event.createdAt)}
            </time>
          </div>
          {event.commitMessage && (
            <p className="mb-1 line-clamp-2 break-words text-sm text-slate-300/78" title={event.commitMessage}>
              {event.commitMessage}
            </p>
          )}
          {event.errorMessage && (
            <p className="mb-1 line-clamp-2 break-words text-sm text-red-300/85" title={event.errorMessage}>
              {event.errorMessage}
            </p>
          )}
          <div className="flex flex-wrap items-center gap-3 text-sm text-slate-400">
            <span className="font-mono text-slate-300">{event.appName}</span>
            {event.commitSha && (
              <span className="rounded-md border border-white/[0.08] bg-black/30 px-2 py-1 font-mono text-xs text-slate-300">
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
