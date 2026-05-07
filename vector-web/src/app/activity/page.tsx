"use client";

/**
 * Activity page — F4
 *
 * Shows every deploy, restart, and crash across the workspace, newest first.
 * Filter pills (All / Deploys / Failures) morph via layoutId animation.
 * Deploy operations collapse into one row with a live mini-stepper.
 * SSE-arrived rows animate in from the top with reflow.
 */

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/design/primitives/PageHeader";
import { FilterPills } from "@/components/activity/FilterPills";
import { EventList } from "@/components/activity/EventList";
import { useEvents } from "@/hooks/useEvents";
import type { DeploymentEvent, DeploymentEventType } from "@/types/vector";
import type { ActivityFilter } from "@/components/activity/FilterPills";

// ── Filter logic ─────────────────────────────────────────────────────────────

const DEPLOY_TYPES = new Set<DeploymentEventType>([
  "DEPLOY_TRIGGERED",
  "DEPLOY_STARTED",
  "PULL_STARTED",
  "PULL_FINISHED",
  "CONTAINER_CREATING",
  "CONTAINER_STARTED",
  "BUILD_STARTED",
  "BUILD_FINISHED",
  "DEPLOY_FINISHED",
  "HEALTH_OK",
  "MANUAL_ROLLBACK",
]);

const FAILURE_TYPES = new Set<DeploymentEventType>([
  "FAILED",
  "CRASHED",
]);

function applyFilter(events: DeploymentEvent[], filter: ActivityFilter): DeploymentEvent[] {
  if (filter === "all") return events;
  if (filter === "deploys") return events.filter((e) => DEPLOY_TYPES.has(e.eventType));
  if (filter === "failures") return events.filter((e) => FAILURE_TYPES.has(e.eventType));
  return events;
}

// ── Inner component (inside Suspense for useSearchParams) ────────────────────

function ActivityContent() {
  const searchParams = useSearchParams();
  const rawFilter = searchParams.get("filter") ?? "all";
  const filter: ActivityFilter = (["all", "deploys", "failures"].includes(rawFilter)
    ? rawFilter
    : "all") as ActivityFilter;

  const { events, isLoading } = useEvents({ limit: 100 });
  const filtered = applyFilter(events, filter);

  return (
    <div
      style={{
        maxWidth: 740,
        width: "100%",
      }}
    >
      <PageHeader
        kicker="HISTORY"
        title="Activity"
        subtitle="Every deploy, restart, and crash across your workspace, newest first."
        actions={<FilterPills />}
      />

      {isLoading && events.length === 0 ? (
        // Loading skeleton
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 16,
          }}
        >
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              style={{
                display: "flex",
                gap: 18,
                opacity: 1 - i * 0.25,
              }}
            >
              <div
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: "50%",
                  background: M_SURFACE,
                  border: `1px solid ${M_LINE}`,
                  flexShrink: 0,
                }}
              />
              <div
                style={{
                  flex: 1,
                  height: 72,
                  borderRadius: 10,
                  background: M_SURFACE,
                  border: `1px solid ${M_LINE}`,
                  opacity: 0.6,
                }}
              />
            </div>
          ))}
        </div>
      ) : (
        <EventList events={filtered} />
      )}
    </div>
  );
}

// Inline token constants to avoid import at page level (keeps the skeleton clean)
const M_SURFACE = "#0E0E12";
const M_LINE = "rgba(255,255,255,0.06)";

// ── Page export ──────────────────────────────────────────────────────────────

export default function ActivityPage() {
  return (
    <AppShell>
      <Suspense>
        <ActivityContent />
      </Suspense>
    </AppShell>
  );
}
