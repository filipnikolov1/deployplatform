"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import type { EvidenceItem } from "@/types/analyzer";

interface LogLine {
  id: number;
  tSec: number; // seconds offset from startTime
  level: "info" | "warn" | "error";
  text: string;
}

interface Props {
  evidence: EvidenceItem[];
  replayProgress: number; // seconds offset (0 = start)
  isPlaying: boolean;
  startTime: number; // epoch ms
}

const ERROR_RX = /(error|exception|fatal|panic|traceback|caused by)/i;
const WARN_RX = /(warn|warning)/i;

function parseLines(evidence: EvidenceItem[], startMs: number): LogLine[] {
  return evidence
    .filter((e) => e.type === "log")
    .map((e) => {
      const ts = e.timestamp ? Date.parse(e.timestamp) : NaN;
      const content = typeof e.content === "string" ? e.content : JSON.stringify(e.content);
      const level: LogLine["level"] =
        e.source === "stderr" || ERROR_RX.test(content)
          ? "error"
          : WARN_RX.test(content)
          ? "warn"
          : "info";
      return {
        id: e.id,
        tSec: Number.isFinite(ts) ? (ts - startMs) / 1000 : 0,
        level,
        text: content,
      };
    })
    .filter((l) => l.tSec >= 0)
    .sort((a, b) => a.tSec - b.tSec);
}

const LEVEL_COLOR: Record<LogLine["level"], string> = {
  info: M.accentLight,
  warn: M.warn,
  error: M.err,
};

export function LogsPanel({ evidence, replayProgress, isPlaying, startTime }: Props) {
  const lines = useMemo(() => parseLines(evidence, startTime), [evidence, startTime]);
  const visibleLines = lines.filter((l) => l.tSec <= replayProgress);
  const containerRef = useRef<HTMLDivElement>(null);

  // Auto-scroll during replay
  useEffect(() => {
    if (!isPlaying || !containerRef.current) return;
    containerRef.current.scrollTop = containerRef.current.scrollHeight;
  }, [visibleLines.length, isPlaying]);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        minHeight: 320,
      }}
    >
      {/* Header */}
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          color: M.fg3,
          letterSpacing: "0.16em",
          textTransform: "uppercase",
          marginBottom: 10,
          fontFamily: M.fontMono,
        }}
      >
        LOGS · T={replayProgress.toFixed(1)}s
      </div>

      {/* Body */}
      <div
        ref={containerRef}
        style={{
          flex: 1,
          background: M.bg,
          border: `1px solid ${M.line}`,
          borderRadius: M.rLg,
          padding: 16,
          fontFamily: M.fontMono,
          fontSize: 12,
          lineHeight: 1.7,
          overflowY: "auto",
          minHeight: 0,
        }}
      >
        {lines.length === 0 ? (
          <div style={{ color: M.fg3, padding: "24px 0", textAlign: "center" }}>
            No logs captured for this crash window.
          </div>
        ) : (
          visibleLines.map((line) => (
            <motion.div
              key={line.id}
              initial={{ opacity: 0, x: -4 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.18 }}
              style={{ display: "flex", gap: 12 }}
            >
              <span style={{ color: M.fg4, flexShrink: 0, minWidth: 36 }}>
                {line.tSec.toFixed(1)}s
              </span>
              <span
                style={{
                  width: 36,
                  fontSize: 10,
                  fontWeight: 600,
                  letterSpacing: "0.08em",
                  textTransform: "uppercase",
                  flexShrink: 0,
                  color: LEVEL_COLOR[line.level],
                  paddingTop: 2,
                }}
              >
                {line.level}
              </span>
              <span
                style={{
                  color: line.level === "error" ? M.err : M.fg,
                  wordBreak: "break-word",
                }}
              >
                {line.text}
              </span>
            </motion.div>
          ))
        )}

        {/* Blinking cursor during replay */}
        {isPlaying && visibleLines.length < lines.length && (
          <motion.span
            animate={{ opacity: [1, 0, 1] }}
            transition={{ duration: 0.8, repeat: Infinity }}
            style={{
              display: "inline-block",
              width: 8,
              height: 14,
              background: M.fg2,
              marginLeft: 84,
              marginTop: 4,
              verticalAlign: "bottom",
            }}
          >
            ▮
          </motion.span>
        )}
      </div>
    </div>
  );
}
