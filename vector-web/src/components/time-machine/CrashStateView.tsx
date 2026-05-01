"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, RefreshCcw, X } from "lucide-react";
import { useCrashAnalysis } from "@/hooks/useCrashAnalysis";
import type { EvidenceItem } from "@/types/analyzer";
import { LogPlaybackPanel } from "./LogPlaybackPanel";
import type { PlaybackSpeed } from "./PlaybackControls";
import { SuspectCodePanel } from "./SuspectCodePanel";
import { ScrubFileHistory } from "./ScrubFileHistory";
import { ReceiptsPanel } from "./ReceiptsPanel";
import { AIAnalysisPanel } from "./AIAnalysisPanel";

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
  const rafRef = useRef<number | null>(null);
  const lastTickRef = useRef<number>(0);

  useEffect(() => {
    setCurrentTime(startTime);
    setIsPlaying(false);
  }, [startTime]);

  // Reset scrubbed file SHA whenever the analysis changes
  useEffect(() => {
    setScrubbedSha(null);
  }, [analysis?.id]);

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

  const handleJumpToReceipt = (id: number) => {
    setHighlightReceipt(id);
    const item = evidence.find((e) => e.id === id);
    if (item?.type === "log" && item.timestamp) {
      const t = Date.parse(item.timestamp);
      if (Number.isFinite(t)) setCurrentTime(t);
    }
    setTimeout(() => {
      const el = document.getElementById(`receipt-${id}`);
      if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
    }, 50);
  };

  const signals = analysis?.signals;
  const longSinceDeploy = (signals?.timeSinceDeployMinutes ?? 0) > 1440;
  const recurring = (signals?.crashCountForCommit ?? 0) > 1;

  if (isLoading) {
    return (
      <div
        className="rounded-xl px-6 py-12 text-center text-[13px]"
        style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)", color: "var(--c-fg-3)" }}
      >
        Loading crash analysis…
      </div>
    );
  }
  if (error || !analysis) {
    return (
      <div
        className="rounded-xl px-6 py-12 text-center text-[13px]"
        style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)", color: "var(--c-fg-3)" }}
      >
        Crash analysis unavailable.
        <button
          type="button"
          onClick={() => refresh()}
          className="ml-2 underline"
          style={{ color: "var(--c-accent-fg)" }}
        >
          Retry
        </button>
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
          <SuspectCodePanel
            appName={appName}
            suspectSha={fileSha}
            filePath={analysis.suspectFilePath}
            highlightLine={analysis.suspectLine}
          />
          <ScrubFileHistory
            appName={appName}
            filePath={analysis.suspectFilePath}
            initialSha={analysis.suspectCommitSha}
            onSelect={setScrubbedSha}
          />
        </div>

        <div className="flex flex-col gap-4">
          <AIAnalysisPanel narration={analysis.aiNarration} />
          <ReceiptsPanel
            evidence={evidence}
            highlightId={highlightReceipt}
            onJump={handleJumpToReceipt}
          />
        </div>
      </div>
    </div>
  );
}
