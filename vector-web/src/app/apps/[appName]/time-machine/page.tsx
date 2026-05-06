"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import useSWR from "swr";
import { ArrowLeft, CheckCircle2, Pin, Unplug } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { useTimeline } from "@/hooks/useTimeline";
import { useAppStats } from "@/hooks/useAppStats";
import { useRecentDeploys } from "@/hooks/useRecentDeploys";
import { useCommitsAhead } from "@/hooks/useCommitsAhead";
import { CommitDetail } from "@/components/time-machine/CommitDetail";
import { CrashStateView } from "@/components/time-machine/CrashStateView";
import { RecentDeploysList } from "@/components/time-machine/RecentDeploysList";
import { LoadingPanelState, PanelState } from "@/components/time-machine/PanelState";
import { TimeMachinePanelBoundary } from "@/components/time-machine/TimeMachinePanelBoundary";
import { TimelineList } from "@/components/time-machine/TimelineList";
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

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return tag === "input" || tag === "textarea" || tag === "select" || target.isContentEditable;
}

function ShaComparisonIndicator({
  currentSha,
  latestSha,
  pinned,
  commitsAhead,
  loading,
  error,
}: {
  currentSha: string | null;
  latestSha: string | null;
  pinned: boolean;
  commitsAhead: number | null;
  loading: boolean;
  error?: Error;
}) {
  if (loading) {
    return (
      <span className="text-[11px]" style={{ color: "var(--c-fg-3)" }}>
        checking latest...
      </span>
    );
  }

  const upToDate = !!currentSha && !!latestSha && currentSha === latestSha;
  const unavailable = error || commitsAhead == null || !currentSha;
  const label = upToDate
    ? "up to date"
    : unavailable
    ? "latest comparison unavailable"
    : commitsAhead > 0
    ? `behind latest by ${commitsAhead} commit${commitsAhead === 1 ? "" : "s"}`
    : latestSha
    ? "latest differs from running SHA"
    : "no latest SHA recorded";
  const Icon = upToDate ? CheckCircle2 : Unplug;

  return (
    <span
      className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
      style={{
        background: upToDate ? "var(--c-status-running-bg)" : "var(--c-surface-2)",
        color: upToDate ? "var(--c-status-running-fg)" : "var(--c-fg-2)",
        border: `1px solid ${upToDate ? "var(--c-status-running-line)" : "var(--c-border-2)"}`,
      }}
      title={pinned && !upToDate ? "Pinned app may be intentionally behind latest" : label}
    >
      <Icon className="h-3 w-3" />
      {label}
    </span>
  );
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
  const commitPanelRef = useRef<HTMLDivElement>(null);

  const { events, isLoading: timelineLoading, error: timelineError } = useTimeline(appName);
  const { stats, isLoading: statsLoading, error: statsError } = useAppStats(appName);
  const { deploys, isLoading: deploysLoading, error: deploysError, refresh: refreshDeploys } = useRecentDeploys(appName);
  const { commitsAhead, isLoading: commitsAheadLoading, error: commitsAheadError } = useCommitsAhead(appName);
  const { data: app, error: appError } = useSWR<Deployment>(
    `/api/apps/${encodeURIComponent(appName)}`,
    appFetcher,
    { refreshInterval: 15_000 },
  );

  const currentSha = app?.commitSha ?? null;
  const latestSha = app?.latestKnownSha ?? null;
  const pinnedImage = app?.pinnedImage ?? null;
  const analyzerUnavailable = !!timelineError || !!statsError || !!deploysError;
  const apiUnavailable = !!appError;
  const hasCrashes = events.some((event) => event.eventType === "CRASH") || (stats?.crashes30d ?? 0) > 0;
  const selectableEvents = useMemo(
    () => events.filter((event) => event.commitSha || event.eventType === "CRASH"),
    [events],
  );

  useEffect(() => {
    localStorage.setItem("lastTimeMachineApp", appName);
  }, [appName]);

  const scrollCommitPanelIntoView = useCallback(() => {
    window.setTimeout(() => {
      commitPanelRef.current?.scrollIntoView({ block: "start", behavior: "smooth" });
    }, 50);
  }, []);

  const selectSha = useCallback(
    (sha: string) => {
      setSelectedSha(sha);
      scrollCommitPanelIntoView();
    },
    [scrollCommitPanelIntoView],
  );

  const closeCrashView = useCallback(() => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("crash");
    const qs = params.toString();
    router.replace(qs ? `/apps/${encodeURIComponent(appName)}/time-machine?${qs}` : `/apps/${encodeURIComponent(appName)}/time-machine`);
  }, [appName, router, searchParams]);

  const openCrashView = useCallback((id: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("crash", String(id));
    router.replace(`/apps/${encodeURIComponent(appName)}/time-machine?${params.toString()}`);
  }, [appName, router, searchParams]);

  const stepTimeline = useCallback(
    (direction: 1 | -1) => {
      if (selectableEvents.length === 0) return;
      const currentIndex = selectedSha
        ? selectableEvents.findIndex((event) => event.commitSha === selectedSha)
        : -1;
      const nextIndex =
        currentIndex < 0
          ? direction > 0
            ? 0
            : selectableEvents.length - 1
          : Math.min(selectableEvents.length - 1, Math.max(0, currentIndex + direction));
      const next = selectableEvents[nextIndex];
      if (!next) return;
      if (next.eventType === "CRASH" && next.sourceEventId != null) {
        openCrashView(next.sourceEventId);
      } else if (next.commitSha) {
        selectSha(next.commitSha);
      }
    },
    [openCrashView, selectSha, selectableEvents, selectedSha],
  );

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (isTypingTarget(event.target)) return;
      if (event.key === "ArrowRight" || event.key === "ArrowDown") {
        event.preventDefault();
        stepTimeline(1);
      } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
        event.preventDefault();
        stepTimeline(-1);
      } else if (event.key === " ") {
        if (crashId) {
          event.preventDefault();
          window.dispatchEvent(new CustomEvent("time-machine-toggle-playback"));
        }
      } else if (event.key === "Escape") {
        event.preventDefault();
        if (crashId) closeCrashView();
        else if (selectedSha) setSelectedSha(null);
        else router.push("/");
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [closeCrashView, crashId, router, selectedSha, stepTimeline]);

  return (
    <div className="flex flex-col">
      <div
        className="mb-4 rounded-xl px-4 py-3 text-[13px] lg:hidden"
        style={{
          background: "var(--c-surface-1)",
          border: "1px solid var(--c-border-1)",
          color: "var(--c-fg-2)",
        }}
      >
        Time Machine is desktop-only for now. Use a viewport at least 1024px wide for the timeline, crash replay, and code panels.
      </div>
      <div className="hidden lg:block">
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
        <div className="ml-auto">
          <ShaComparisonIndicator
            currentSha={currentSha}
            latestSha={latestSha}
            pinned={!!pinnedImage}
            commitsAhead={commitsAhead.count}
            loading={commitsAheadLoading}
            error={commitsAheadError}
          />
        </div>
      </div>

      {apiUnavailable && (
        <div
          className="mb-4 rounded-xl px-4 py-3 text-[13px]"
          style={{
            background: "var(--c-status-building-bg)",
            border: "1px solid var(--c-status-building-line)",
            color: "var(--c-status-building-fg)",
          }}
        >
          vector-api is unreachable. Time Machine can still show analyzer history, but deployment actions and live app metadata are unavailable.
        </div>
      )}

      {analyzerUnavailable && (
        <div className="mb-4 rounded-xl overflow-hidden" style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}>
          <PanelState
            tone="danger"
            title="Time Machine unavailable"
            message="vector-analyzer is unreachable or returned an error. The dashboard can continue working, but historical timeline data is temporarily unavailable."
          />
        </div>
      )}

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
        ) : (
          <div className="col-span-4">
            <PanelState
              tone="warning"
              title="Stats unavailable"
              message="The analyzer could not load aggregate Time Machine stats."
            />
          </div>
        )}
      </div>

      {crashId && (
        <div className="mb-6">
          <TimeMachinePanelBoundary panelName="Crash replay" resetKey={crashId}>
            <CrashStateView appName={appName} crashId={crashId} onClose={closeCrashView} />
          </TimeMachinePanelBoundary>
        </div>
      )}

      {!crashId && !timelineLoading && !timelineError && !hasCrashes && (
        <div
          className="mb-4 rounded-xl px-4 py-3 text-[12px]"
          style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)", color: "var(--c-fg-3)" }}
        >
          No crashes captured yet. Crash reconstruction will appear here when the analyzer records a crash event.
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4">
        {/* Left: timeline + commit detail */}
        <div className="flex flex-col gap-4">
          {/* Timeline */}
          <TimeMachinePanelBoundary panelName="Timeline" resetKey={`${appName}:${events.length}`}>
            <div
              className="rounded-xl overflow-hidden"
              style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
            >
              <div
                className="px-4 py-3"
                style={{ borderBottom: "1px solid var(--c-border-1)" }}
              >
                <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
                  Timeline - last 14 days
                </span>
              </div>
              {timelineLoading ? (
                <LoadingPanelState label="Loading timeline events..." />
              ) : timelineError ? (
                <PanelState
                  tone="danger"
                  title="Timeline unavailable"
                  message="vector-analyzer could not be reached. Retry when the analyzer service is back online."
                />
              ) : events.length === 0 ? (
                <PanelState
                  title="No deploy history yet"
                  message="No deploys, crashes, restarts, or commits are recorded for this app in Time Machine. If this app predates the Vector rename, history before the analyzer cutoff is not available."
                />
              ) : (
                <TimelineList
                  events={events}
                  selectedSha={selectedSha}
                  onSelect={selectSha}
                  onOpenCrash={openCrashView}
                  renderItem={(props) => <TimelineItem key={props.event.id} {...props} />}
                />
              )}
            </div>
          </TimeMachinePanelBoundary>

          {/* Commit detail panel */}
          <div ref={commitPanelRef}>
            <TimeMachinePanelBoundary panelName="Commit detail" resetKey={selectedSha}>
              {selectedSha ? (
                <CommitDetail
                  appName={appName}
                  sha={selectedSha}
                  currentSha={currentSha}
                  deploys={deploys}
                  pinnedImage={pinnedImage}
                  apiUnavailable={apiUnavailable}
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
            </TimeMachinePanelBoundary>
          </div>
        </div>

        {/* Right: recent deploys */}
        <TimeMachinePanelBoundary panelName="Recent deploys" resetKey={`${appName}:${deploys.length}`}>
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
              {deploysLoading ? (
                <LoadingPanelState label="Loading recent deploys..." />
              ) : deploysError ? (
                <PanelState
                  tone="danger"
                  title="Deploy history unavailable"
                  message="vector-analyzer could not load recent deploys for this app."
                />
              ) : (
                <RecentDeploysList
                  deploys={deploys}
                  timelineEvents={events}
                  currentSha={currentSha}
                  selectedSha={selectedSha}
                  onSelect={selectSha}
                />
              )}
            </div>
          </div>
        </TimeMachinePanelBoundary>
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
