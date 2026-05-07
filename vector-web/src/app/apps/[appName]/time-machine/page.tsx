"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { M, MMOTION } from "@/design/tokens";
import { PageHeader } from "@/design/primitives/PageHeader";
import { Button } from "@/design/primitives/Button";
import { Section } from "@/design/primitives/Section";
import { AppShell } from "@/components/shell/AppShell";

// Time machine components
import { CrashHeader, CrashAppSelector } from "@/components/time-machine/CrashHeader";
import { HorizontalTimeline } from "@/components/time-machine/HorizontalTimeline";
import type { TimelineNode } from "@/components/time-machine/HorizontalTimeline";
import { LogsPanel } from "@/components/time-machine/LogsPanel";
import { CodePanel } from "@/components/time-machine/CodePanel";
import { AiExplanation } from "@/components/time-machine/AiExplanation";
import { DiffSideBySide } from "@/components/time-machine/DiffSideBySide";
import { RollBack } from "@/components/time-machine/RollBack";
import { HealthyStats } from "@/components/time-machine/HealthyStats";
import { RecentDeploys } from "@/components/time-machine/RecentDeploys";
import { CommitDetail } from "@/components/time-machine/CommitDetail";
import { ReceiptsPanel } from "@/components/time-machine/ReceiptsPanel";

// Hooks
import { useCrashAnalysis } from "@/hooks/useCrashAnalysis";
import { useTimeline } from "@/hooks/useTimeline";
import { useRecentDeploys } from "@/hooks/useRecentDeploys";
import { useAppStats } from "@/hooks/useAppStats";
import { useAppUptime, useAvgPull } from "@/hooks/useHealthyStats";
import useSWR from "swr";
import type { TimelineEvent } from "@/types/analyzer";
import type { Deployment } from "@/types/deployment";

// ────────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────────

const DAY_MS = 86_400_000;
const WINDOW_MS = 14 * DAY_MS;

/** Convert a TimelineEvent list into HorizontalTimeline nodes (14-day window). */
function toTimelineNodes(events: TimelineEvent[], now: Date): TimelineNode[] {
  const windowStart = new Date(now.getTime() - WINDOW_MS);
  return events
    .filter((e) => {
      const d = new Date(e.occurredAt);
      return d >= windowStart && d <= now;
    })
    .map((e) => {
      const d = new Date(e.occurredAt);
      const t = (d.getTime() - windowStart.getTime()) / WINDOW_MS;
      const kind: TimelineNode["kind"] =
        e.eventType === "CRASH"
          ? "crash"
          : e.eventType === "RESTART"
          ? "restart"
          : e.eventType === "COMMIT"
          ? "commit"
          : "deploy";
      return {
        id: e.id,
        t: Math.max(0, Math.min(1, t)),
        kind,
        sha: e.commitSha || undefined,
        occurredAt: d,
        msg: (() => {
          try {
            return JSON.parse(e.metadata)?.message ?? undefined;
          } catch {
            return undefined;
          }
        })(),
      };
    });
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return tag === "input" || tag === "textarea" || tag === "select" || target.isContentEditable;
}

const appFetcher = async (url: string): Promise<Deployment> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(10_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

// ────────────────────────────────────────────────────────────────────────────
// Crash Mode
// ────────────────────────────────────────────────────────────────────────────

function CrashMode({
  appName,
  crashId,
  onCloseCrash,
}: {
  appName: string;
  crashId: string;
  onCloseCrash: () => void;
}) {
  const { analysis, isLoading, error, refresh } = useCrashAnalysis(appName, crashId);
  const [isPlaying, setIsPlaying] = useState(false);
  const [replayProgress, setReplayProgress] = useState(0);
  const [scrub, setScrub] = useState(1.0);
  const [scrubbedSha, setScrubbedSha] = useState<string | null>(null);
  const [highlightReceipt, setHighlightReceipt] = useState<number | null>(null);
  const [receiptsVisible, setReceiptsVisible] = useState(true);
  const [isRegenerating, setIsRegenerating] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const { events } = useTimeline(appName);
  const now = useMemo(() => new Date(), []);
  const nodes = useMemo(() => toTimelineNodes(events, now), [events, now]);

  // Crash node to show in timeline
  const crashNodes = nodes.filter((n) => n.kind === "crash");

  // Replay logic (100ms cadence per second of log time)
  const maxLogSec = useMemo(() => {
    if (!analysis?.evidence) return 10;
    const logs = analysis.evidence.filter((e) => e.type === "log" && e.timestamp);
    if (logs.length === 0) return 10;
    const times = logs.map((e) => Date.parse(e.timestamp ?? "")).filter(Number.isFinite);
    if (times.length < 2) return 10;
    return (Math.max(...times) - Math.min(...times)) / 1000;
  }, [analysis]);

  const startMs = useMemo(() => {
    if (!analysis?.evidence) return Date.now() - 10_000;
    const times = analysis.evidence
      .filter((e) => e.type === "log" && e.timestamp)
      .map((e) => Date.parse(e.timestamp ?? ""))
      .filter(Number.isFinite);
    return times.length > 0 ? Math.min(...times) : Date.now() - 10_000;
  }, [analysis]);

  useEffect(() => {
    if (!isPlaying) {
      if (intervalRef.current) clearInterval(intervalRef.current);
      return;
    }
    setReplayProgress(0);
    intervalRef.current = setInterval(() => {
      setReplayProgress((t) => {
        if (t >= maxLogSec) {
          setIsPlaying(false);
          return maxLogSec;
        }
        return t + 0.1;
      });
    }, 100);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [isPlaying, maxLogSec]);

  const handleReplay = () => {
    setReplayProgress(0);
    setIsPlaying(true);
  };

  const handleJumpToReceipt = useCallback(
    (id: number) => {
      setHighlightReceipt(id);
      setReceiptsVisible(true);
      const item = analysis?.evidence.find((e) => e.id === id);
      if (item?.type === "log" && item.timestamp) {
        const t = Date.parse(item.timestamp);
        if (Number.isFinite(t)) {
          const sec = (t - startMs) / 1000;
          setReplayProgress(Math.max(0, sec));
        }
      }
      setTimeout(() => {
        const el = document.getElementById(`receipt-${id}`);
        if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
      }, 50);
    },
    [analysis, startMs]
  );

  const handleRegenerate = useCallback(async () => {
    if (!analysis || isRegenerating) return;
    setIsRegenerating(true);
    try {
      await fetch(
        `/api/analyzer/apps/${encodeURIComponent(appName)}/crashes/${encodeURIComponent(crashId)}/regenerate`,
        { method: "POST" }
      );
      await refresh();
    } catch {
      // ignore
    } finally {
      setIsRegenerating(false);
    }
  }, [analysis, appName, crashId, isRegenerating, refresh]);

  // SSE listener for narration completion
  useEffect(() => {
    if (!analysis || analysis.aiNarrationStatus !== "PENDING") return;
    const es = new EventSource(`/api/analyzer/apps/${encodeURIComponent(appName)}/stream`);
    const handler = (e: MessageEvent) => {
      try {
        const data = typeof e.data === "string" ? JSON.parse(e.data) : null;
        if (data && Number(data.crashId) === Number(crashId)) refresh();
      } catch {
        // ignore
      }
    };
    es.addEventListener("narrationCompleted", handler);
    es.addEventListener("narrationFailed", handler);
    return () => {
      es.removeEventListener("narrationCompleted", handler);
      es.removeEventListener("narrationFailed", handler);
      es.close();
    };
  }, [analysis, appName, crashId, refresh]);

  if (isLoading) {
    return (
      <div
        style={{
          padding: 40,
          textAlign: "center",
          color: M.fg3,
          fontSize: 13,
        }}
      >
        Loading crash analysis...
      </div>
    );
  }

  if (error || !analysis) {
    return (
      <div
        style={{
          padding: 24,
          background: M.surface,
          border: `1px solid ${M.line}`,
          borderRadius: M.rLg,
          color: M.fg3,
          fontSize: 13,
        }}
      >
        Crash analysis unavailable. vector-analyzer may be down.{" "}
        <button
          type="button"
          onClick={() => refresh()}
          style={{
            color: M.accentLight,
            background: "none",
            border: "none",
            cursor: "pointer",
            textDecoration: "underline",
          }}
        >
          Retry
        </button>
      </div>
    );
  }

  const fileSha = scrubbedSha ?? analysis.suspectCommitSha;
  const crashedAt = analysis.signals?.crashedAt ? new Date(analysis.signals.crashedAt) : null;

  // Working side: last good commit sha
  const workingContent = ""; // populated from CommitDetail / diff
  const brokenContent = "";

  return (
    <motion.div
      key="crash-mode"
      {...MMOTION.page}
      style={{ display: "flex", flexDirection: "column", gap: 32 }}
    >
      {/* Crash Header */}
      <CrashHeader
        appName={appName}
        apps={[appName]}
        crashedAt={crashedAt}
        exitCode={null}
        onAppChange={() => {}}
        onReplay={handleReplay}
        isPlaying={isPlaying}
        onTogglePlay={() => setIsPlaying((p) => !p)}
      />

      {/* App + crash label row */}
      <CrashAppSelector
        appName={appName}
        apps={[appName]}
        crashLabel={
          crashedAt
            ? `crashed ${Math.round((Date.now() - crashedAt.getTime()) / 60_000)}m ago`
            : "crash details"
        }
        onAppChange={() => {}}
      />

      {/* Horizontal Timeline */}
      <Section title="Timeline" hint="Drag to scrub. Click a node to jump.">
        <HorizontalTimeline
          nodes={nodes}
          scrub={scrub}
          onScrub={setScrub}
          onNodeClick={(node) => {
            if (node.kind === "crash" && typeof node.id === "number") {
              const crashEvent = events.find(
                (e) => e.id === node.id && e.eventType === "CRASH"
              );
              // Note: crash nodes have no direct navigation in this mode — already on crash
            }
          }}
        />
      </Section>

      {/* Logs + Code split */}
      <motion.section
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.32 }}
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: 16,
        }}
      >
        <LogsPanel
          evidence={analysis.evidence}
          replayProgress={replayProgress}
          isPlaying={isPlaying}
          startTime={startMs}
        />
        <CodePanel
          appName={appName}
          suspectSha={fileSha}
          filePath={analysis.suspectFilePath}
          highlightLine={analysis.suspectLine}
          onVersionChange={(sha) => setScrubbedSha(sha)}
        />
      </motion.section>

      {/* AI Explanation */}
      <Section title="What broke">
        <AiExplanation
          narration={analysis.aiNarration}
          status={analysis.aiNarrationStatus}
          failureReason={analysis.aiFailureReason}
          providerUsed={analysis.aiProviderUsed}
          regenerateCount={analysis.aiRegenerateCount}
          regenerateLimit={analysis.aiRegenerateLimit}
          onJumpToReceipt={handleJumpToReceipt}
          onRegenerate={handleRegenerate}
          isRegenerating={isRegenerating}
        />
      </Section>

      {/* Receipts panel */}
      {receiptsVisible && (
        <ReceiptsPanel
          evidence={analysis.evidence}
          highlightId={highlightReceipt}
          onJump={handleJumpToReceipt}
        />
      )}
      <div style={{ display: "flex", justifyContent: "flex-end" }}>
        <Button
          variant="ghost"
          size="sm"
          leadingIcon={receiptsVisible ? "eye-off" : "eye"}
          onClick={() => setReceiptsVisible((v) => !v)}
        >
          {receiptsVisible ? "Hide receipts" : "Show receipts"}
        </Button>
      </div>

      {/* Diff side by side */}
      {analysis.lastGoodCommitSha && analysis.suspectCommitSha && (
        <Section title="Diff" hint="Working version vs. broken version.">
          <DiffSideBySide
            filePath={analysis.suspectFilePath}
            working={{
              sha: analysis.lastGoodCommitSha,
              label: "WORKING",
              content: "",
              age: null,
            }}
            broken={{
              sha: analysis.suspectCommitSha,
              label: "BROKEN",
              content: "",
              age: crashedAt,
            }}
            highlightLine={analysis.suspectLine}
          />
        </Section>
      )}

      {/* Roll Back */}
      {analysis.lastGoodCommitSha && analysis.suspectCommitSha && (
        <RollBack
          appName={appName}
          fromSha={analysis.suspectCommitSha}
          toSha={analysis.lastGoodCommitSha}
          estimatedDeployMs={null}
          onSuccess={() => {}}
        />
      )}
    </motion.div>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Healthy Mode
// ────────────────────────────────────────────────────────────────────────────

function HealthyMode({
  appName,
  onOpenCrash,
}: {
  appName: string;
  onOpenCrash: (id: number) => void;
}) {
  const [selectedSha, setSelectedSha] = useState<string | null>(null);
  const [scrub, setScrub] = useState(0.85);
  const commitPanelRef = useRef<HTMLDivElement>(null);

  const { events } = useTimeline(appName);
  const { deploys, isLoading: deploysLoading, refresh: refreshDeploys } = useRecentDeploys(appName);
  const { uptime, isLoading: uptimeLoading } = useAppUptime(appName);
  const { avgPull, isLoading: avgLoading } = useAvgPull(appName);
  const { stats } = useAppStats(appName);
  const { data: app } = useSWR<Deployment>(
    `/api/apps/${encodeURIComponent(appName)}`,
    appFetcher,
    { refreshInterval: 15_000 }
  );

  // Derive "last crash" from events
  const lastCrashAt = useMemo(() => {
    const crashEvent = events.find((e) => e.eventType === "CRASH");
    return crashEvent ? new Date(crashEvent.occurredAt) : null;
  }, [events]);

  const now = useMemo(() => new Date(), []);
  const nodes = useMemo(() => toTimelineNodes(events, now), [events, now]);
  const healthyNodes = nodes.filter((n) => n.kind !== "crash");

  const currentSha = app?.commitSha ?? null;

  const handleNodeClick = useCallback(
    (node: TimelineNode) => {
      if (node.sha) {
        setSelectedSha(node.sha);
        setTimeout(() => {
          commitPanelRef.current?.scrollIntoView({ block: "start", behavior: "smooth" });
        }, 50);
      }
    },
    []
  );

  const statusLine =
    app?.status === "RUNNING"
      ? `running · last deploy ${stats?.lastDeployedAt ? Math.round((Date.now() - new Date(stats.lastDeployedAt).getTime()) / DAY_MS) + "d ago" : "recently"}`
      : app?.status
      ? `${app.status.toLowerCase()} · last deploy recently`
      : null;

  return (
    <motion.div
      key="healthy-mode"
      {...MMOTION.page}
      style={{ display: "flex", flexDirection: "column", gap: 32 }}
    >
      {/* Header */}
      <div>
        <PageHeader
          kicker="APP HISTORY"
          title="Time Machine"
          actions={
            app?.subdomain ? (
              <Button
                variant="ghost"
                leadingIcon="external-link"
                size="sm"
                onClick={() =>
                  window.open(`https://${app.subdomain}`, "_blank")
                }
              >
                Open app
              </Button>
            ) : undefined
          }
        />
        {statusLine && (
          <div
            style={{
              marginTop: -40,
              marginBottom: 24,
              fontSize: 13,
              color: M.fg3,
              fontFamily: M.fontMono,
            }}
          >
            {statusLine}
          </div>
        )}
      </div>

      {/* Healthy stats */}
      <HealthyStats
        uptimePercent={uptime?.percent ?? null}
        deploysCount={stats?.deploys30d ?? null}
        lastCrashAt={lastCrashAt}
        avgPullMs={avgPull?.avgMs ?? null}
        isLoading={uptimeLoading || avgLoading}
      />

      {/* Horizontal Timeline */}
      <Section
        title="Timeline"
        hint="14 days. Click any deploy to inspect."
      >
        <HorizontalTimeline
          nodes={healthyNodes}
          scrub={scrub}
          onScrub={setScrub}
          onNodeClick={handleNodeClick}
        />
      </Section>

      {/* Commit Detail modal-like panel */}
      <div ref={commitPanelRef}>
        <AnimatePresence mode="wait">
          {selectedSha && (
            <motion.div
              key={selectedSha}
              {...MMOTION.modal}
            >
              <CommitDetail
                appName={appName}
                sha={selectedSha}
                currentSha={currentSha}
                deploys={deploys}
                pinnedImage={app?.pinnedImage ?? null}
                apiUnavailable={false}
                onClose={() => setSelectedSha(null)}
                onRollbackSuccess={() => {
                  void refreshDeploys();
                  setSelectedSha(null);
                }}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Recent Deploys */}
      <Section title="Recent deploys" hint="Click any commit to see what changed.">
        {deploysLoading ? (
          <div
            style={{
              height: 120,
              borderRadius: M.rLg,
              background: M.surface,
              border: `1px solid ${M.line}`,
            }}
          />
        ) : (
          <RecentDeploys
            deploys={deploys}
            currentSha={currentSha}
            selectedSha={selectedSha}
            onSelect={(sha) => {
              setSelectedSha(sha);
              setTimeout(() => {
                commitPanelRef.current?.scrollIntoView({ block: "start", behavior: "smooth" });
              }, 50);
            }}
          />
        )}
      </Section>
    </motion.div>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Page root
// ────────────────────────────────────────────────────────────────────────────

function TimeMachineContent({ appName }: { appName: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const crashId = searchParams.get("crash");

  const openCrashView = useCallback(
    (id: number) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("crash", String(id));
      router.replace(
        `/apps/${encodeURIComponent(appName)}/time-machine?${params.toString()}`
      );
    },
    [appName, router, searchParams]
  );

  const closeCrashView = useCallback(() => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("crash");
    const qs = params.toString();
    router.replace(
      qs
        ? `/apps/${encodeURIComponent(appName)}/time-machine?${qs}`
        : `/apps/${encodeURIComponent(appName)}/time-machine`
    );
  }, [appName, router, searchParams]);

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (isTypingTarget(e.target)) return;
      if (e.key === " " && crashId) {
        e.preventDefault();
        window.dispatchEvent(new CustomEvent("time-machine-toggle-playback"));
      } else if (e.key === "Escape") {
        e.preventDefault();
        if (crashId) closeCrashView();
        else router.push("/");
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [closeCrashView, crashId, router]);

  // Desktop-only note
  return (
    <div>
      <div
        style={{
          marginBottom: 16,
          padding: "12px 16px",
          borderRadius: M.rMd,
          background: M.surface,
          border: `1px solid ${M.line}`,
          color: M.fg3,
          fontSize: 13,
          display: "none", // shown only on mobile via @media — use className below
        }}
        className="lg:hidden block"
      >
        Time Machine is desktop-only. Use a viewport ≥1024px wide.
      </div>

      <div className="hidden lg:block">
        <AnimatePresence mode="wait">
          {crashId ? (
            <CrashMode
              key={crashId}
              appName={appName}
              crashId={crashId}
              onCloseCrash={closeCrashView}
            />
          ) : (
            <HealthyMode
              key="healthy"
              appName={appName}
              onOpenCrash={openCrashView}
            />
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

export default function TimeMachinePage({
  params,
}: {
  params: { appName: string };
}) {
  return (
    <AppShell>
      <TimeMachineContent appName={params.appName} />
    </AppShell>
  );
}
