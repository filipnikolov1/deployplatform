"use client";

import { useEffect } from "react";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { useTimeline } from "@/hooks/useTimeline";
import { useAppStats } from "@/hooks/useAppStats";
import type { TimelineEvent, TimelineEventType } from "@/types/analyzer";

const EVENT_CONFIG: Record<TimelineEventType, { label: string; color: string; bg: string }> = {
  DEPLOY:  { label: "Deploy",  color: "var(--c-status-running-fg)",  bg: "var(--c-status-running-bg)"  },
  CRASH:   { label: "Crash",   color: "var(--c-status-failed-fg)",   bg: "var(--c-status-failed-bg)"   },
  RESTART: { label: "Restart", color: "var(--c-status-building-fg)", bg: "var(--c-status-building-bg)" },
  COMMIT:  { label: "Commit",  color: "var(--c-fg-2)",               bg: "var(--c-surface-2)"           },
};

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div
      className="rounded-xl p-4"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      <div
        className="text-[11px] uppercase tracking-[0.1em]"
        style={{ color: "var(--c-fg-3)" }}
      >
        {label}
      </div>
      <div
        className="text-2xl font-semibold tabular-nums mt-1"
        style={{ color: "var(--c-fg-0)" }}
      >
        {value}
      </div>
    </div>
  );
}

function TimelineItem({ event }: { event: TimelineEvent }) {
  const cfg = EVENT_CONFIG[event.eventType] ?? EVENT_CONFIG.COMMIT;
  const date = new Date(event.occurredAt);

  return (
    <div
      className="flex items-center gap-3 py-3"
      style={{ borderBottom: "1px solid var(--c-border-1)" }}
    >
      <span
        className="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase"
        style={{ background: cfg.bg, color: cfg.color }}
      >
        {cfg.label}
      </span>
      <div className="min-w-0 flex-1">
        {event.commitSha ? (
          <span
            className="font-mono text-[11px] rounded px-1.5 py-0.5"
            style={{ background: "var(--c-surface-2)", color: "var(--c-fg-2)" }}
          >
            {event.commitSha.slice(0, 7)}
          </span>
        ) : null}
      </div>
      <span
        className="shrink-0 text-[12px] tabular-nums"
        style={{ color: "var(--c-fg-3)" }}
      >
        {date.toLocaleDateString()} {date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
      </span>
    </div>
  );
}

function TimeMachineContent({ appName }: { appName: string }) {
  const { events, isLoading: timelineLoading } = useTimeline(appName);
  const { stats, isLoading: statsLoading } = useAppStats(appName);

  useEffect(() => {
    localStorage.setItem("lastTimeMachineApp", appName);
  }, [appName]);

  return (
    <div className="flex flex-col">
      <div
        className="flex items-center gap-2 pb-5 mb-6"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <Link
          href="/"
          className="flex items-center gap-1.5 text-[13px] transition-opacity hover:opacity-70"
          style={{ color: "var(--c-fg-3)" }}
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Apps
        </Link>
        <span style={{ color: "var(--c-border-2)" }}>/</span>
        <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-2)" }}>
          {appName}
        </span>
        <span style={{ color: "var(--c-border-2)" }}>/</span>
        <span className="text-[13px] font-semibold" style={{ color: "var(--c-fg-0)" }}>
          Time Machine
        </span>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
        {statsLoading ? (
          Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="h-20 rounded-xl skeleton-shimmer"
              style={{ background: "var(--c-surface-1)" }}
            />
          ))
        ) : stats ? (
          <>
            <StatCard label="Deploys (30d)"  value={stats.deploys30d}  />
            <StatCard label="Crashes (30d)"  value={stats.crashes30d}  />
            <StatCard label="Restarts (30d)" value={stats.restarts30d} />
            <StatCard label="Commits (30d)"  value={stats.commits30d}  />
          </>
        ) : null}
      </div>

      <div
        className="rounded-xl overflow-hidden"
        style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
      >
        <div
          className="px-4 py-3"
          style={{ borderBottom: "1px solid var(--c-border-1)" }}
        >
          <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
            Timeline — last 14 days
          </span>
        </div>
        {timelineLoading ? (
          <div className="p-4 text-[13px]" style={{ color: "var(--c-fg-3)" }}>
            Loading…
          </div>
        ) : events.length === 0 ? (
          <div className="p-8 text-center text-[13px]" style={{ color: "var(--c-fg-3)" }}>
            No events in this period.
          </div>
        ) : (
          <div className="px-4">
            {events.map((e) => (
              <TimelineItem key={e.id} event={e} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default function TimeMachinePage({ params }: { params: { appName: string } }) {
  return (
    <ToastProvider>
      <AppShell>
        <TimeMachineContent appName={params.appName} />
      </AppShell>
    </ToastProvider>
  );
}
