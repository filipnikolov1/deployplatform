"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import useSWR from "swr";
import { ArrowLeft, Pin } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { useTimeline } from "@/hooks/useTimeline";
import { useAppStats } from "@/hooks/useAppStats";
import { useRecentDeploys } from "@/hooks/useRecentDeploys";
import { CommitDetail } from "@/components/time-machine/CommitDetail";
import { CrashStateView } from "@/components/time-machine/CrashStateView";
import { RecentDeploysList } from "@/components/time-machine/RecentDeploysList";
import type { TimelineEvent, TimelineEventType } from "@/types/analyzer";
import type { Deployment } from "@/types/deployment";

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
      <div className="text-[11px] uppercase tracking-[0.1em]" style={{ color: "var(--c-fg-3)" }}>
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

function isUpdateAvailable(event: TimelineEvent): boolean {
  try {
    const m = JSON.parse(event.metadata);
    return m.eventType === "UPDATE_AVAILABLE";
  } catch {
    return false;
  }
}

function isRollback(event: TimelineEvent): boolean {
  try {
    const m = JSON.parse(event.metadata);
    return m.triggerSource === "ROLLBACK" || m.eventType === "MANUAL_ROLLBACK";
  } catch {
    return false;
  }
}

function TimelineItem({
  event,
  onSelect,
  onOpenCrash,
  isSelected,
}: {
  event: TimelineEvent;
  onSelect: (sha: string) => void;
  onOpenCrash: (id: number) => void;
  isSelected: boolean;
}) {
  const cfg = EVENT_CONFIG[event.eventType] ?? EVENT_CONFIG.COMMIT;
  const date = new Date(event.occurredAt);
  const updateAvail = isUpdateAvailable(event);
  const rollback = isRollback(event);

  const handleClick = () => {
    if (event.eventType === "CRASH" && event.sourceEventId != null) {
      onOpenCrash(event.sourceEventId);
      return;
    }
    if (event.commitSha) onSelect(event.commitSha);
  };

  return (
    <div
      className="flex items-center gap-3 py-3 cursor-pointer transition-all"
      style={{
        borderBottom: "1px solid var(--c-border-1)",
        background: isSelected ? "rgba(var(--c-accent-rgb,130,80,255),0.06)" : "transparent",
      }}
      onClick={handleClick}
    >
      {updateAvail ? (
        <span
          className="shrink-0 h-2 w-2 rounded-full"
          style={{ background: "var(--c-status-building-fg)", marginLeft: "4px" }}
          title="Update available (app is pinned)"
        />
      ) : (
        <span
          className="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase"
          style={{ background: cfg.bg, color: cfg.color }}
        >
          {cfg.label}
        </span>
      )}
      <div className="min-w-0 flex-1 flex items-center gap-2">
        {event.commitSha ? (
          <span
            className="font-mono text-[11px] rounded px-1.5 py-0.5"
            style={{ background: "var(--c-surface-2)", color: "var(--c-fg-2)" }}
          >
            {event.commitSha.slice(0, 7)}
          </span>
        ) : null}
        {rollback && (
          <span
            className="text-[10px] rounded px-1 py-0.5"
            style={{ background: "var(--c-surface-2)", color: "var(--c-fg-3)", border: "1px solid var(--c-border-2)" }}
          >
            ↺ rollback
          </span>
        )}
        {updateAvail && (
          <span className="text-[11px]" style={{ color: "var(--c-status-building-fg)" }}>
            update available
          </span>
        )}
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

const appFetcher = async (url: string): Promise<Deployment> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(10_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

function TimeMachineContent({ appName }: { appName: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const crashId = searchParams.get("crash");
  const [selectedSha, setSelectedSha] = useState<string | null>(null);

  const { events, isLoading: timelineLoading } = useTimeline(appName);
  const { stats, isLoading: statsLoading } = useAppStats(appName);
  const { deploys, refresh: refreshDeploys } = useRecentDeploys(appName);
  const { data: app } = useSWR<Deployment>(
    `/api/apps/${encodeURIComponent(appName)}`,
    appFetcher,
    { refreshInterval: 15_000 },
  );

  const currentSha = app?.commitSha ?? null;
  const pinnedImage = app?.pinnedImage ?? null;

  useEffect(() => {
    localStorage.setItem("lastTimeMachineApp", appName);
  }, [appName]);

  const closeCrashView = () => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("crash");
    const qs = params.toString();
    router.replace(qs ? `/apps/${encodeURIComponent(appName)}/time-machine?${qs}` : `/apps/${encodeURIComponent(appName)}/time-machine`);
  };

  const openCrashView = (id: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("crash", String(id));
    router.replace(`/apps/${encodeURIComponent(appName)}/time-machine?${params.toString()}`);
  };

  return (
    <div className="flex flex-col">
      {/* Header */}
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
        {pinnedImage && (
          <span
            className="ml-2 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
            style={{
              background: "var(--c-accent-soft)",
              color: "var(--c-accent-fg)",
              border: "1px solid var(--c-accent-line)",
            }}
          >
            <Pin className="h-3 w-3" />
            pinned
          </span>
        )}
      </div>

      {/* Stats cards */}
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

      {crashId && (
        <div className="mb-6">
          <CrashStateView appName={appName} crashId={crashId} onClose={closeCrashView} />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4">
        {/* Left: timeline + commit detail */}
        <div className="flex flex-col gap-4">
          {/* Timeline */}
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
              <div
                className="p-8 text-center text-[13px]"
                style={{ color: "var(--c-fg-3)" }}
              >
                No events in this period.
              </div>
            ) : (
              <div className="px-4">
                {events.map((e) => (
                  <TimelineItem
                    key={e.id}
                    event={e}
                    onSelect={setSelectedSha}
                    onOpenCrash={openCrashView}
                    isSelected={!!e.commitSha && e.commitSha === selectedSha}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Commit detail panel */}
          {selectedSha ? (
            <CommitDetail
              appName={appName}
              sha={selectedSha}
              currentSha={currentSha}
              deploys={deploys}
              pinnedImage={pinnedImage}
              onClose={() => setSelectedSha(null)}
              onRollbackSuccess={() => {
                void refreshDeploys();
                setSelectedSha(null);
              }}
            />
          ) : (
            <div
              className="flex items-center justify-center rounded-xl py-8 text-[13px]"
              style={{
                background: "var(--c-surface-1)",
                border: "1px solid var(--c-border-1)",
                color: "var(--c-fg-3)",
              }}
            >
              Select a commit or deploy event above to view details.
            </div>
          )}
        </div>

        {/* Right: recent deploys */}
        <div
          className="rounded-xl overflow-hidden self-start"
          style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
        >
          <div
            className="px-4 py-3"
            style={{ borderBottom: "1px solid var(--c-border-1)" }}
          >
            <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
              Recent Deploys
            </span>
          </div>
          <div className="p-2">
            <RecentDeploysList
              deploys={deploys}
              timelineEvents={events}
              currentSha={currentSha}
              selectedSha={selectedSha}
              onSelect={setSelectedSha}
            />
          </div>
        </div>
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
