"use client";

import { useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { useEvents } from "@/hooks/useEvents";
import { ActivityTimeline } from "@/components/activity/ActivityTimeline";
import { EmptyState } from "@/components/dashboard/EmptyState";
import { Button } from "@/components/primitives/Button";
import type { DeploymentEventType } from "@/types/launchpad";

type FilterKey =
  | "all"
  | "deploys"
  | "builds"
  | "failures"
  | "rollbacks"
  | "ignored";

const filterMap: Record<FilterKey, DeploymentEventType[] | null> = {
  all: null,
  deploys: ["DEPLOY_TRIGGERED", "DEPLOY_STARTED", "DEPLOY_FINISHED"],
  builds: ["BUILD_STARTED", "BUILD_FINISHED"],
  failures: ["FAILED", "CRASHED"],
  rollbacks: ["MANUAL_ROLLBACK", "PIN_RELEASED"],
  ignored: ["WEBHOOK_IGNORED"],
};

export default function ActivityPage() {
  const [filter, setFilter] = useState<FilterKey>("all");
  const { events, isLoading } = useEvents({ limit: 50 });
  const allowed = filterMap[filter];
  const filtered = allowed
    ? events.filter((e) => allowed.includes(e.eventType))
    : events;

  return (
    <ToastProvider>
      <AppShell>
        <div className="mx-auto max-w-5xl py-6">
          <section className="px-1 sm:px-0">
            <header className="glass-page-header">
              <div className="glass-kicker">Signal Feed</div>
              <h1 className="glass-title">Activity</h1>
              <p className="glass-subtitle">
                Recent deploys, failures, rollbacks, and automation events across the stack.
              </p>
            </header>

            <div
              className="mb-7 flex flex-wrap gap-2.5"
              role="group"
              aria-label="Filter events"
            >
              {(Object.keys(filterMap) as FilterKey[]).map((key) => (
                <button
                  key={key}
                  onClick={() => setFilter(key)}
                  aria-pressed={filter === key}
                  className={`rounded-full border px-4 py-2.5 text-sm font-medium backdrop-blur-xl transition-colors focus:outline-none focus-visible:ring-focus ${
                    filter === key
                      ? "border-white/[0.14] bg-white/[0.09] text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]"
                      : "border-white/[0.08] bg-black/30 text-slate-300/75 hover:bg-black/45"
                  }`}
                >
                  {key[0].toUpperCase() + key.slice(1)}
                </button>
              ))}
            </div>

            <div aria-live="polite">
              {isLoading ? (
                <div className="glass-card-strong h-32 animate-pulse" />
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
                <ActivityTimeline events={filtered} />
              )}
            </div>
          </section>
        </div>
      </AppShell>
    </ToastProvider>
  );
}
