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
        <div className="mx-auto max-w-4xl px-4 py-12">
          <header className="mb-12">
            <h1 className="text-3xl font-semibold tracking-tight text-slate-100 mb-2">
              Activity
            </h1>
            <p className="text-slate-400">Recent deploys and build events</p>
          </header>

          <div
            className="flex gap-2 mb-6 flex-wrap"
            role="group"
            aria-label="Filter events"
          >
            {(Object.keys(filterMap) as FilterKey[]).map((key) => (
              <button
                key={key}
                onClick={() => setFilter(key)}
                aria-pressed={filter === key}
                className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors focus:outline-none focus-visible:ring-focus ${
                  filter === key
                    ? "bg-accent-ghost/30 text-white border border-accent-ghostLight"
                    : "bg-white/5 text-slate-400 border border-white/10 hover:bg-white/10"
                }`}
              >
                {key[0].toUpperCase() + key.slice(1)}
              </button>
            ))}
          </div>

          <div aria-live="polite">
            {isLoading ? (
              <div className="h-32 rounded-xl bg-white/[0.02] animate-pulse" />
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
        </div>
      </AppShell>
    </ToastProvider>
  );
}
