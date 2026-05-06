"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, EyeOff, Eye, RefreshCcw, X } from "lucide-react";
import { useCrashAnalysis } from "@/hooks/useCrashAnalysis";
import type { EvidenceItem } from "@/types/analyzer";
import { LogPlaybackPanel } from "./LogPlaybackPanel";
import type { PlaybackSpeed } from "./PlaybackControls";
import { SuspectCodePanel } from "./SuspectCodePanel";
import { ScrubFileHistory } from "./ScrubFileHistory";
import { ReceiptsPanel } from "./ReceiptsPanel";
import { AIAnalysisPanel } from "./AIAnalysisPanel";
import { LoadingPanelState, PanelState } from "./PanelState";
import { TimeMachinePanelBoundary } from "./TimeMachinePanelBoundary";

const ERROR_RX = /(error|exception|fatal|panic|traceback|caused by)/i;

function logTimestamps(evidence: EvidenceItem[]): number[] {
  return evidence
    .filter((e) => e.type === "log" && e.timestamp)
    .map((e) => Date.parse(e.timestamp ?? ""))
    .filter((n) => Number.isFinite(n) && n > 0);
}

function findErrorTimestamp(evidence: EvidenceItem[]): number | null {
  const errors = evidence.filter((e) => {
    if (e.type !== "log") return false;
    const c = typeof e.content === "string" ? e.content : "";
    return e.source === "stderr" || ERROR_RX.test(c);
  });
  for (const e of errors) {
    if (e.timestamp) {
      const t = Date.parse(e.timestamp);
      if (Number.isFinite(t) && t > 0) return t;
    }
  }
  return null;
}

function formatDays(minutes: number | null): string {
  if (minutes == null) return "";
  const days = Math.floor(minutes / 1440);
  if (days >= 2) return `${days} days ago`;
  const hours = Math.floor(minutes / 60);
  return `${hours} hours ago`;
}

interface Props {
  appName: string;
  crashId: string;
  onClose: () => void;
}

export function CrashStateView({ appName, crashId, onClose }: Props) {
  const { analysis, isLoading, error, refresh } = useCrashAnalysis(appName, crashId);

  const evidence = analysis?.evidence ?? [];
  const errorTs = useMemo(() => findErrorTimestamp(evidence), [evidence]);
  const { startTime, endTime } = useMemo(() => {
    const ts = logTimestamps(evidence);
    if (ts.length === 0) {
      const now = Date.now();
      return { startTime: now - 60_000, endTime: now };
    }
    return { startTime: Math.min(...ts), endTime: Math.max(...ts) };
  }, [evidence]);

  const [currentTime, setCurrentTime] = useState(startTime);
  const [isPlaying, setIsPlaying] = useState(false);
  const [speed, setSpeed] = useState<PlaybackSpeed>(1);
  const [highlightReceipt, setHighlightReceipt] = useState<number | null>(null);
  const [scrubbedSha, setScrubbedSha] = useState<string | null>(null);
  const [receiptsVisible, setReceiptsVisible] = useState(true);
  const [isRegenerating, setIsRegenerating] = useState(false);
  const rafRef = useRef<number | null>(null);
  const lastTickRef = useRef<number>(0);

  useEffect(() => {
    setCurrentTime(startTime);
    setIsPlaying(false);
  }, [startTime]);

  useEffect(() => {
    const toggle = () => {
      setIsPlaying((playing) => !playing);
    };
    window.addEventListener("time-machine-toggle-playback", toggle);
    return () => window.removeEventListener("time-machine-toggle-playback", toggle);
  }, []);

  // Reset scrubbed file SHA whenever the analysis changes
  useEffect(() => {
    setScrubbedSha(null);
  }, [analysis?.id]);

  // Listen for narration completion/failure on the analyzer SSE stream and
  // re-fetch the analysis when our crash is the one that finished. The hook's
  // 30s dedupingInterval means without this nudge the spinner could hang.
  useEffect(() => {
    if (!analysis || analysis.aiNarrationStatus !== "PENDING") return;
    const es = new EventSource(`/api/analyzer/apps/${encodeURIComponent(appName)}/stream`);
    const handler = (e: MessageEvent) => {
      try {
        const data = typeof e.data === "string" ? JSON.parse(e.data) : null;
        if (data && Number(data.crashId) === Number(crashId)) {
          refresh();
        }
      } catch {
        // ignore — stream contains non-JSON heartbeats too
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

  useEffect(() => {
    if (!isPlaying) {
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
      return;
    }
    lastTickRef.current = performance.now();
    const loop = (now: number) => {
      const delta = now - lastTickRef.current;
      lastTickRef.current = now;
      setCurrentTime((t) => {
        const next = t + delta * speed;
        if (next >= endTime) {
          setIsPlaying(false);
          return endTime;
        }
        return next;
      });
      rafRef.current = requestAnimationFrame(loop);
    };
    rafRef.current = requestAnimationFrame(loop);
    return () => {
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    };
  }, [isPlaying, speed, endTime]);

  const handleReplay = () => {
    setCurrentTime(startTime);
    setIsPlaying(true);
  };

  const handleJumpToReceipt = useCallback(
    (id: number) => {
      setHighlightReceipt(id);
      // If receipts are hidden, reveal them — otherwise the citation click
      // would silently do nothing and look broken.
      setReceiptsVisible(true);
      const item = evidence.find((e) => e.id === id);
      if (item?.type === "log" && item.timestamp) {
        const t = Date.parse(item.timestamp);
        if (Number.isFinite(t)) setCurrentTime(t);
      }
      setTimeout(() => {
        const el = document.getElementById(`receipt-${id}`);
        if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
      }, 50);
    },
    [evidence],
  );

  const handleRegenerate = useCallback(async () => {
    if (!analysis || isRegenerating) return;
    setIsRegenerating(true);
    try {
      const res = await fetch(
        `/api/analyzer/apps/${encodeURIComponent(appName)}/crashes/${encodeURIComponent(crashId)}/regenerate`,
        { method: "POST" },
      );
      // 429 means the per-crash retry cap is exhausted — still refresh so the
      // server's authoritative count lands and we can disable the button.
      if (!res.ok && res.status !== 429) {
        console.warn("regenerate failed:", res.status);
      }
      await refresh();
    } catch (e) {
      console.warn("regenerate threw:", e);
    } finally {
      setIsRegenerating(false);
    }
  }, [analysis, appName, crashId, isRegenerating, refresh]);

  const signals = analysis?.signals;
  const longSinceDeploy = (signals?.timeSinceDeployMinutes ?? 0) > 1440;
  const recurring = (signals?.crashCountForCommit ?? 0) > 1;

  if (isLoading) {
    return (
      <div
        className="rounded-xl"
        style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
      >
        <LoadingPanelState label="Loading crash analysis..." />
      </div>
    );
  }
  if (error || !analysis) {
    return (
      <div
        className="rounded-xl"
        style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
      >
        <PanelState
          tone="danger"
          title="Crash analysis unavailable"
          message="vector-analyzer could not load this crash. If the analyzer is down, the rest of the dashboard can still work."
          action={
            <button
              type="button"
              onClick={() => refresh()}
              className="rounded-md px-3 py-1.5 text-[12px] font-medium"
              style={{
                background: "var(--c-accent-soft)",
                color: "var(--c-accent-fg)",
                border: "1px solid var(--c-accent-line)",
              }}
            >
              Retry
            </button>
          }
        />
      </div>
    );
  }

  const fileSha = scrubbedSha ?? analysis.suspectCommitSha;

  return (
    <div className="flex flex-col gap-4">
      {/* Crash header */}
      <div
        className="flex items-center justify-between rounded-xl px-4 py-3"
        style={{
          background: "var(--c-status-failed-bg)",
          border: "1px solid var(--c-status-failed-fg)",
          color: "var(--c-status-failed-fg)",
        }}
      >
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4" />
          <span className="text-[13px] font-semibold uppercase tracking-[0.08em]">
            Crash · {appName}
          </span>
          {analysis.suspectCommitSha && (
            <span
              className="font-mono text-[11px] rounded px-1.5 py-0.5 ml-2"
              style={{
                background: "var(--c-surface-2)",
                color: "var(--c-fg-1)",
                border: "1px solid var(--c-border-2)",
              }}
            >
              suspect {analysis.suspectCommitSha.slice(0, 7)}
            </span>
          )}
          {recurring && (
            <span
              className="text-[11px] rounded-full px-2 py-0.5 ml-1 font-medium"
              style={{
                background: "var(--c-surface-2)",
                color: "var(--c-status-failed-fg)",
                border: "1px solid var(--c-status-failed-fg)",
              }}
            >
              <RefreshCcw className="inline h-2.5 w-2.5 mr-1" />
              recurring · {signals?.crashCountForCommit}× under this version
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="inline-flex items-center justify-center h-7 w-7 rounded-full transition-[filter]"
          style={{ background: "var(--c-surface-2)", color: "var(--c-fg-2)", border: "1px solid var(--c-border-2)" }}
          aria-label="Close crash view"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>

      {longSinceDeploy && (
        <div
          className="rounded-xl px-4 py-3 text-[12px]"
          style={{
            background: "var(--c-surface-1)",
            border: "1px solid var(--c-border-2)",
            color: "var(--c-fg-2)",
          }}
        >
          <strong>Last deploy was {formatDays(signals?.timeSinceDeployMinutes ?? 0)}.</strong>{" "}
          This crash may be unrelated to recent code changes.
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4">
        <div className="flex flex-col gap-4">
          <TimeMachinePanelBoundary panelName="Log playback" resetKey={analysis.id}>
            <LogPlaybackPanel
              evidence={evidence}
              errorTimestamp={errorTs}
              currentTime={currentTime}
              isPlaying={isPlaying}
              speed={speed}
              startTime={startTime}
              endTime={endTime}
              onPlay={() => setIsPlaying(true)}
              onPause={() => setIsPlaying(false)}
              onReplay={handleReplay}
              onSpeedChange={setSpeed}
              onScrub={(t) => {
                setIsPlaying(false);
                setCurrentTime(t);
              }}
            />
          </TimeMachinePanelBoundary>
          <TimeMachinePanelBoundary panelName="Suspect code" resetKey={`${fileSha}:${analysis.suspectFilePath}`}>
            <SuspectCodePanel
              appName={appName}
              suspectSha={fileSha}
              filePath={analysis.suspectFilePath}
              highlightLine={analysis.suspectLine}
            />
          </TimeMachinePanelBoundary>
          <TimeMachinePanelBoundary panelName="File history" resetKey={analysis.suspectFilePath}>
            <ScrubFileHistory
              appName={appName}
              filePath={analysis.suspectFilePath}
              initialSha={analysis.suspectCommitSha}
              onSelect={setScrubbedSha}
            />
          </TimeMachinePanelBoundary>
        </div>

        <div className="flex flex-col gap-4">
          <TimeMachinePanelBoundary panelName="AI analysis" resetKey={analysis.id}>
            <AIAnalysisPanel
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
          </TimeMachinePanelBoundary>
          <div className="flex justify-end">
            <button
              type="button"
              onClick={() => setReceiptsVisible((v) => !v)}
              className="inline-flex items-center gap-1.5 text-[11px] px-2 py-1 rounded-md transition-[filter] hover:brightness-110"
              style={{
                background: "var(--c-surface-2)",
                color: "var(--c-fg-2)",
                border: "1px solid var(--c-border-2)",
              }}
            >
              {receiptsVisible ? <EyeOff className="h-3 w-3" /> : <Eye className="h-3 w-3" />}
              {receiptsVisible ? "Hide receipts" : "Show receipts"}
            </button>
          </div>
          {receiptsVisible && (
            <TimeMachinePanelBoundary panelName="Receipts" resetKey={analysis.id}>
              <ReceiptsPanel
                evidence={evidence}
                highlightId={highlightReceipt}
                onJump={handleJumpToReceipt}
              />
            </TimeMachinePanelBoundary>
          )}
        </div>
      </div>
    </div>
  );
}
