"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { PlaybackControls, type PlaybackSpeed } from "./PlaybackControls";
import type { EvidenceItem } from "@/types/analyzer";

interface LogLine {
  id: number;
  timestamp: number; // epoch ms
  stream: string;
  content: string;
  isError: boolean;
}

interface Props {
  evidence: EvidenceItem[];
  errorTimestamp: number | null;
  currentTime: number;
  isPlaying: boolean;
  speed: PlaybackSpeed;
  startTime: number;
  endTime: number;
  onPlay: () => void;
  onPause: () => void;
  onReplay: () => void;
  onSpeedChange: (s: PlaybackSpeed) => void;
  onScrub: (t: number) => void;
}

const ERROR_RX = /(error|exception|fatal|panic|traceback|caused by)/i;

function parseLogLines(evidence: EvidenceItem[]): LogLine[] {
  return evidence
    .filter((e) => e.type === "log")
    .map((e) => {
      const ts = e.timestamp ? Date.parse(e.timestamp) : NaN;
      const content = typeof e.content === "string" ? e.content : JSON.stringify(e.content);
      const isError = e.source === "stderr" || ERROR_RX.test(content);
      return {
        id: e.id,
        timestamp: Number.isFinite(ts) ? ts : 0,
        stream: typeof e.source === "string" ? e.source : "stdout",
        content,
        isError,
      };
    })
    .filter((l) => l.timestamp > 0)
    .sort((a, b) => a.timestamp - b.timestamp);
}

export function LogPlaybackPanel({
  evidence,
  errorTimestamp,
  currentTime,
  isPlaying,
  speed,
  startTime,
  endTime,
  onPlay,
  onPause,
  onReplay,
  onSpeedChange,
  onScrub,
}: Props) {
  const lines = useMemo(() => parseLogLines(evidence), [evidence]);
  const [flashOn, setFlashOn] = useState(false);
  const lastErrorPassRef = useRef(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Trigger red flash when currentTime crosses the error timestamp
  useEffect(() => {
    if (errorTimestamp == null) return;
    const passed = currentTime >= errorTimestamp;
    if (passed && !lastErrorPassRef.current) {
      setFlashOn(true);
      const t = setTimeout(() => setFlashOn(false), 800);
      lastErrorPassRef.current = true;
      return () => clearTimeout(t);
    }
    if (!passed && lastErrorPassRef.current) {
      lastErrorPassRef.current = false;
      setFlashOn(false);
    }
  }, [currentTime, errorTimestamp]);

  // Auto-scroll the active line into view during playback
  const visibleCount = lines.filter((l) => l.timestamp <= currentTime).length;
  useEffect(() => {
    if (!isPlaying || !containerRef.current) return;
    const el = containerRef.current.querySelector<HTMLDivElement>(`[data-idx="${visibleCount - 1}"]`);
    if (el) el.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [visibleCount, isPlaying]);

  const span = Math.max(1, endTime - startTime);
  const scrubPct = Math.min(100, Math.max(0, ((currentTime - startTime) / span) * 100));

  return (
    <div
      className="rounded-xl overflow-hidden flex flex-col"
      style={{
        background: "var(--c-surface-1)",
        border: "1px solid var(--c-border-1)",
        boxShadow: flashOn ? "0 0 0 2px var(--c-status-failed-fg) inset" : "none",
        transition: "box-shadow 200ms ease-out",
      }}
    >
      <div
        className="flex items-center justify-between px-4 py-3"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <div className="flex items-center gap-2">
          <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
            Logs — playback
          </span>
          <span
            className="rounded-full px-2 py-0.5 text-[10px] font-medium tabular-nums"
            style={{
              background: "var(--c-surface-2)",
              color: "var(--c-fg-3)",
              border: "1px solid var(--c-border-2)",
            }}
          >
            {visibleCount}/{lines.length}
          </span>
        </div>
        <PlaybackControls
          isPlaying={isPlaying}
          speed={speed}
          onPlay={onPlay}
          onPause={onPause}
          onReplay={onReplay}
          onSpeedChange={onSpeedChange}
        />
      </div>

      <div className="px-4 py-2" style={{ borderBottom: "1px solid var(--c-border-1)" }}>
        <input
          type="range"
          min={startTime}
          max={endTime}
          value={Math.min(endTime, Math.max(startTime, currentTime))}
          onChange={(e) => onScrub(Number(e.target.value))}
          className="w-full"
          style={{ accentColor: "var(--c-accent-fg)" }}
          aria-label="Scrub log timeline"
        />
        <div className="flex justify-between text-[10px] tabular-nums mt-1" style={{ color: "var(--c-fg-3)" }}>
          <span>{new Date(startTime).toLocaleTimeString()}</span>
          <span>{Math.round(scrubPct)}%</span>
          <span>{new Date(endTime).toLocaleTimeString()}</span>
        </div>
      </div>

      <div
        ref={containerRef}
        className="font-mono text-[12px] leading-[18px] overflow-y-auto"
        style={{
          maxHeight: "360px",
          background: "var(--c-surface-2)",
          padding: "8px 0",
        }}
      >
        {lines.length === 0 ? (
          <div className="px-4 py-6 text-center" style={{ color: "var(--c-fg-3)" }}>
            No logs captured for this crash window.
          </div>
        ) : (
          lines.map((line, idx) => {
            const visible = line.timestamp <= currentTime;
            return (
              <div
                key={line.id}
                data-idx={idx}
                className="px-4 py-0.5 flex gap-3"
                style={{
                  opacity: visible ? 1 : 0.25,
                  color: line.isError ? "var(--c-status-failed-fg)" : "var(--c-fg-1)",
                  transition: "opacity 150ms ease-out",
                }}
              >
                <span className="shrink-0 tabular-nums" style={{ color: "var(--c-fg-3)" }}>
                  {new Date(line.timestamp).toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit",
                  })}
                </span>
                <span className="shrink-0 uppercase text-[10px] tracking-wider mt-[2px]" style={{ color: "var(--c-fg-3)" }}>
                  {line.stream}
                </span>
                <span className="whitespace-pre-wrap break-all">{line.content}</span>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
