"use client";

import { RotateCcw } from "lucide-react";
import type { RecentDeploy, TimelineEvent } from "@/types/analyzer";

interface Props {
  deploys: RecentDeploy[];
  timelineEvents: TimelineEvent[];
  currentSha: string | null;
  selectedSha: string | null;
  onSelect: (sha: string) => void;
}

function isRollbackEvent(metadata: string): boolean {
  try {
    const m = JSON.parse(metadata);
    return m.triggerSource === "ROLLBACK" || m.eventType === "MANUAL_ROLLBACK";
  } catch {
    return false;
  }
}

function isUpdateAvailableEvent(event: TimelineEvent): boolean {
  try {
    const m = JSON.parse(event.metadata);
    return m.eventType === "UPDATE_AVAILABLE";
  } catch {
    return false;
  }
}

export function RecentDeploysList({
  deploys,
  timelineEvents,
  currentSha,
  selectedSha,
  onSelect,
}: Props) {
  const updateAvailableEvents = timelineEvents.filter(isUpdateAvailableEvent);

  if (deploys.length === 0) {
    return (
      <div
        className="px-3 py-6 text-center text-[13px]"
        style={{ color: "var(--c-fg-3)" }}
      >
        <div className="font-medium" style={{ color: "var(--c-fg-2)" }}>
          No deploys yet.
        </div>
        <div className="mt-1 text-[12px]">
          The first successful deploy will appear here. History before the Time Machine cutoff is unavailable.
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col">
      {/* Update-available notices */}
      {updateAvailableEvents.map((e) => (
        <div
          key={e.id}
          className="flex items-center gap-2 px-3 py-2 text-[12px] rounded-lg mb-1"
          style={{ background: "var(--c-status-building-bg)", border: "1px solid var(--c-status-building-line)" }}
        >
          <span
            className="h-2 w-2 rounded-full shrink-0"
            style={{ background: "var(--c-status-building-fg)" }}
          />
          <span style={{ color: "var(--c-status-building-fg)" }}>
            Update available —{" "}
            {new Date(e.occurredAt).toLocaleDateString()}{" "}
            {new Date(e.occurredAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </span>
        </div>
      ))}

      {deploys.map((d) => {
        const isCurrent = d.commitSha === currentSha;
        const isSelected = d.commitSha === selectedSha;
        const isRollback = isRollbackEvent(d.metadata);
        const date = new Date(d.occurredAt);

        return (
          <button
            key={d.id}
            type="button"
            onClick={() => onSelect(d.commitSha)}
            className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-all hover:translate-x-0.5"
            style={{
              background: isSelected ? "var(--c-accent-soft)" : "transparent",
              border: `1px solid ${isSelected ? "var(--c-accent-line)" : "transparent"}`,
              cursor: "pointer",
            }}
            onMouseEnter={(e) => {
              if (!isSelected)
                e.currentTarget.style.background = "var(--c-surface-2)";
            }}
            onMouseLeave={(e) => {
              if (!isSelected)
                e.currentTarget.style.background = "transparent";
            }}
          >
            {/* Status dot */}
            <span
              className="h-2 w-2 shrink-0 rounded-full"
              style={{
                background: isCurrent
                  ? "var(--c-status-running-fg)"
                  : "var(--c-fg-3)",
              }}
            />

            {/* SHA */}
            <span
              className="font-mono text-[12px] shrink-0"
              style={{ color: isSelected ? "var(--c-accent-fg)" : "var(--c-fg-2)" }}
            >
              {d.commitSha.slice(0, 7)}
            </span>

            {/* Rollback badge */}
            {isRollback && (
              <span
                className="flex items-center gap-0.5 rounded px-1 py-0.5 text-[10px] font-medium shrink-0"
                style={{
                  background: "var(--c-surface-2)",
                  color: "var(--c-fg-3)",
                  border: "1px solid var(--c-border-2)",
                }}
                title="This was a rollback deployment"
              >
                <RotateCcw className="h-2.5 w-2.5" />
                rollback
              </span>
            )}

            {isCurrent && (
              <span
                className="rounded-full px-1.5 py-0.5 text-[10px] font-semibold shrink-0"
                style={{ background: "var(--c-status-running-bg)", color: "var(--c-status-running-fg)" }}
              >
                running
              </span>
            )}

            <span
              className="ml-auto text-[11px] tabular-nums shrink-0"
              style={{ color: "var(--c-fg-3)" }}
            >
              {date.toLocaleDateString()}{" "}
              {date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          </button>
        );
      })}
    </div>
  );
}
