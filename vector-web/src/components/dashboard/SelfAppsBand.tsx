"use client";

import { useState } from "react";
import { Rocket, Sparkles, Clock, ArrowUpCircle } from "lucide-react";
import { formatRelative } from "@/lib/time";
import { statusConfig, toAppStatus } from "@/lib/statusConfig";
import type { Deployment } from "@/types/deployment";

interface SelfAppsBandProps {
  apps: Deployment[];
  onOpen: (appName: string) => void;
}

function SelfAppCard({ app, onOpen }: { app: Deployment; onOpen: (name: string) => void }) {
  const [hover, setHover] = useState(false);
  const status = toAppStatus(app.status);
  const config = statusConfig[status];
  const hasUpdate = app.isSelfApp && !!app.latestKnownSha && app.commitSha !== app.latestKnownSha;

  return (
    <button
      type="button"
      onClick={() => onOpen(app.appName)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      className="block w-full text-left rounded-[10px] p-3.5 transition-all duration-fast outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
      style={{
        background: hover ? "var(--c-surface-2)" : "var(--c-surface-1)",
        border: "1px solid var(--c-border-2)",
      }}
    >
      {/* App header */}
      <div className="flex items-center gap-2.5 mb-2.5">
        <div
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md"
          style={{
            background: "var(--c-accent-soft)",
            border: "1px solid var(--c-accent-line)",
          }}
        >
          <Rocket className="h-3.5 w-3.5" style={{ color: "var(--c-accent-light)" }} />
        </div>
        <div className="min-w-0 flex-1">
          <div
            className="truncate text-sm font-semibold"
            style={{ color: "var(--c-fg-0)" }}
          >
            {app.appName}
          </div>
          {app.commitSha && (
            <div
              className="mt-px font-mono text-[11px] truncate"
              style={{ color: "var(--c-fg-3)" }}
            >
              {app.commitSha.slice(0, 7)}
            </div>
          )}
        </div>
        {/* Status chip */}
        <span
          className="inline-flex shrink-0 items-center gap-1.5 rounded-full px-2 py-0.5 text-[10.5px] font-medium whitespace-nowrap"
          style={{
            color: config.fg,
            background: config.bg,
            border: `1px solid ${config.line}`,
          }}
        >
          <span
            className="inline-block h-1.5 w-1.5 rounded-full"
            style={{
              background: config.dotColor,
              boxShadow: `0 0 4px ${config.dotColor}`,
            }}
          />
          {config.label}
        </span>
      </div>

      {/* Footer row */}
      <div
        className="flex items-center justify-between text-[11px] pt-2.5"
        style={{
          borderTop: "1px solid var(--c-border-1)",
          color: "var(--c-fg-2)",
        }}
      >
        <span className="inline-flex items-center gap-1">
          <Clock className="h-2.5 w-2.5" />
          {formatRelative(app.updatedAt)}
        </span>
        {hasUpdate ? (
          <span
            className="inline-flex items-center gap-1 font-medium"
            style={{ color: "var(--c-accent-fg)" }}
          >
            <ArrowUpCircle className="h-2.5 w-2.5" />
            Update available
          </span>
        ) : (
          <span style={{ color: "var(--c-fg-3)" }}>Up to date</span>
        )}
      </div>
    </button>
  );
}

export function SelfAppsBand({ apps, onOpen }: SelfAppsBandProps) {
  if (!apps.length) return null;

  return (
    <section className="mb-7">
      <div className="flex items-center gap-2.5 mb-2.5">
        <Sparkles className="h-3.5 w-3.5" style={{ color: "var(--c-accent-light)" }} />
        <h2
          className="text-[13px] font-semibold m-0 uppercase tracking-[0.14em]"
          style={{ color: "var(--c-fg-1)" }}
        >
          Self-apps
        </h2>
        <span className="text-xs" style={{ color: "var(--c-fg-3)" }}>{apps.length}</span>
        <span className="ml-auto text-xs" style={{ color: "var(--c-fg-3)" }}>
          Apps that run Vector itself
        </span>
      </div>
      <div className="grid gap-3" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))" }}>
        {apps.map((app) => (
          <SelfAppCard key={app.appName} app={app} onOpen={onOpen} />
        ))}
      </div>
    </section>
  );
}
