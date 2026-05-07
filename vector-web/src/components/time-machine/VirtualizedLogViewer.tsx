"use client";

import { useCallback, useEffect, useRef } from "react";
import { FixedSizeList as List } from "react-window";
import type { LogEntry } from "@/types/analyzer";

interface Props {
  logs: LogEntry[];
  height?: number;
}

const ROW_HEIGHT = 20;

function LogRow({
  index,
  style,
  data,
}: {
  index: number;
  style: React.CSSProperties;
  data: LogEntry[];
}) {
  const entry = data[index];
  const ts = new Date(entry.timestamp).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  const isStderr = entry.stream === "stderr";

  return (
    <div
      style={{
        ...style,
        display: "flex",
        alignItems: "flex-start",
        gap: "12px",
        paddingLeft: "12px",
        paddingRight: "12px",
        fontFamily: "monospace",
        fontSize: "12px",
        lineHeight: "20px",
        color: isStderr ? "var(--c-status-failed-fg)" : "var(--c-fg-1)",
        background: index % 2 === 0 ? "transparent" : "rgba(255,255,255,0.015)",
      }}
    >
      <span
        style={{
          flexShrink: 0,
          width: "68px",
          color: "var(--c-fg-3)",
          userSelect: "none",
        }}
      >
        {ts}
      </span>
      <span style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
        {entry.line}
      </span>
    </div>
  );
}

export function VirtualizedLogViewer({ logs, height = 400 }: Props) {
  const listRef = useRef<List>(null);

  // Auto-scroll to bottom on new logs
  useEffect(() => {
    if (logs.length > 0) {
      listRef.current?.scrollToItem(logs.length - 1, "end");
    }
  }, [logs.length]);

  const itemData = logs;

  if (logs.length === 0) {
    return (
      <div
        className="flex items-center justify-center text-[12px]"
        style={{
          height,
          color: "var(--c-fg-3)",
          fontFamily: "monospace",
          background: "var(--c-surface-2)",
          borderRadius: "6px",
        }}
      >
        No log entries for this deployment window.
      </div>
    );
  }

  return (
    <div
      style={{
        background: "var(--c-surface-2)",
        borderRadius: "6px",
        overflow: "hidden",
        border: "1px solid var(--c-border-1)",
      }}
    >
      <List
        ref={listRef}
        height={height}
        width="100%"
        itemCount={logs.length}
        itemSize={ROW_HEIGHT}
        itemData={itemData}
        overscanCount={20}
      >
        {LogRow}
      </List>
    </div>
  );
}
