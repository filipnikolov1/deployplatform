"use client";

import { useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { useEvents } from "@/hooks/useEvents";
import { ActivityTimeline } from "@/components/activity/ActivityTimeline";
import { EmptyState } from "@/components/dashboard/EmptyState";
import { Button } from "@/components/primitives/Button";
import type { DeploymentEventType } from "@/types/vector";

type FilterKey =
  | "all"
  | "deploys"
  | "builds"
  | "failures"
  | "rollbacks"
  | "updates"
  | "ignored";

const filterMap: Record<FilterKey, DeploymentEventType[] | null> = {
  all: null,
  deploys: ["DEPLOY_TRIGGERED", "DEPLOY_STARTED", "DEPLOY_FINISHED"],
  builds: ["BUILD_STARTED", "BUILD_FINISHED"],
  failures: ["FAILED", "CRASHED"],
  rollbacks: ["MANUAL_ROLLBACK", "PIN_RELEASED"],
  updates: ["UPDATE_AVAILABLE", "UPDATE_TRIGGERED", "UPDATE_SUCCESS", "UPDATE_FAILED", "SELF_APP_BOOTSTRAPPED"],
  ignored: ["WEBHOOK_IGNORED"],
};

const FILTER_LABELS: Record<FilterKey, string> = {
  all: "All events",
  deploys: "Deploys",
  builds: "Builds",
  failures: "Failures",
  rollbacks: "Rollbacks",
  updates: "Self-updates",
  ignored: "Ignored",
};

function ActivityContent() {
  const [filter, setFilter] = useState<FilterKey>("all");
  const { events, isLoading } = useEvents({ limit: 50 });
  const allowed = filterMap[filter];
  const filtered = allowed
    ? events.filter((e) => allowed.includes(e.eventType))
    : events;

  return (
    <div className="flex flex-col">
      <div
        className="flex items-end justify-between gap-4 pb-5 mb-6 flex-wrap"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <div>
          <h1
            className="text-2xl font-semibold tracking-[-0.01em] m-0"
            style={{ color: "var(--c-fg-0)", lineHeight: 1.2 }}
          >
            Activity
          </h1>
          <p className="mt-1.5 text-[13px]" style={{ color: "var(--c-fg-2)" }}>
            Recent deploys, failures, rollbacks, and automation events.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap gap-1.5 mb-5" role="group" aria-label="Filter events">
        {(Object.keys(filterMap) as FilterKey[]).map((key) => {
          const sel = filter === key;
          return (
            <button
              key={key}
              type="button"
              onClick={() => setFilter(key)}
              aria-pressed={sel}
              className="rounded-full px-3.5 py-[7px] text-[13px] font-medium transition-all duration-fast outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
              style={{
                background: sel ? "var(--c-accent-soft)" : "var(--c-surface-1)",
                border: `1px solid ${sel ? "var(--c-accent-line)" : "var(--c-border-1)"}`,
                color: sel ? "var(--c-accent-fg)" : "var(--c-fg-2)",
                cursor: "pointer",
              }}
            >
              {FILTER_LABELS[key]}
            </button>
          );
        })}
      </div>

      <div aria-live="polite">
        {isLoading ? (
          <div
            className="h-32 rounded-xl skeleton-shimmer"
            style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
          />
        ) : filtered.length === 0 ? (
          <EmptyState>
            <EmptyState.Media />
            <EmptyState.Title>No activity yet</EmptyState.Title>
            <EmptyState.Description>
              Deploys and build events will appear here
            </EmptyState.Description>
            <EmptyState.Actions>
              <Link href="/setup">
                <Button>Deploy your first app</Button>
              </Link>
            </EmptyState.Actions>
          </EmptyState>
        ) : (
          <div
            className="rounded-xl overflow-hidden"
            style={{
              background: "var(--c-surface-1)",
              border: "1px solid var(--c-border-1)",
            }}
          >
            <ActivityTimeline events={filtered} />
          </div>
        )}
      </div>
    </div>
  );
}

export default function ActivityPage() {
  return (
    <ToastProvider>
      <AppShell>
        <ActivityContent />
      </AppShell>
    </ToastProvider>
  );
}
