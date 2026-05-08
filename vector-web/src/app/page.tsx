"use client";

/**
 * Dashboard — Apps page (F3).
 *
 * F3.1 PageHeader with dynamic subtitle + Search + New App actions.
 * F3.2 StatTiles with filter state.
 * F3.3 SelfAppsSection (staggered cards, Update flow, Time Machine).
 * F3.4 AppsTable (all normal apps filtered by stat tile, drawer via ?app=).
 *
 * Drawer: AppDetailDrawer mounts when ?app= search param is present.
 * Preserves the existing ?app= contract so the command palette + deep-links work.
 */

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { formatDistanceToNow } from "date-fns";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/design/primitives/PageHeader";
import { Button } from "@/design/primitives/Button";
import { Section } from "@/design/primitives/Section";
import { M } from "@/design/tokens";
import { StatTiles, type StatFilter } from "@/components/dashboard/StatTiles";
import { SelfAppsSection } from "@/components/dashboard/SelfAppsSection";
import { AppsTable } from "@/components/dashboard/AppsTable";
import { AppDetailDrawer } from "@/components/detail/AppDetailDrawer";
import { commandPaletteStore } from "@/hooks/useCommandPalette";
import { useApps } from "@/hooks/useApps";
import { useEvents } from "@/hooks/useEvents";
import { toAppStatus } from "@/lib/statusConfig";
import type { DeploymentEventType } from "@/types/vector";

// Event types that indicate the app needs attention
const ATTENTION_TYPES = new Set<DeploymentEventType>(["FAILED", "CRASHED"]);

function DashboardInner() {
  const { apps, isLoading } = useApps();
  const { events } = useEvents({ limit: 100 });

  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  // Drawer state — driven by ?app= search param
  const [openAppName, setOpenAppName] = useState<string | null>(null);

  useEffect(() => {
    const appParam = searchParams.get("app");
    if (appParam) {
      setOpenAppName(appParam);
    } else {
      setOpenAppName(null);
    }
  }, [searchParams]);

  const handleDrawerClose = () => {
    setOpenAppName(null);
    const params = new URLSearchParams(searchParams.toString());
    params.delete("app");
    const qs = params.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname);
  };

  // Partition apps
  const selfApps = apps.filter((a) => a.isSelfApp);
  const normalApps = apps.filter((a) => !a.isSelfApp);

  // Stat counts
  const counts = {
    all: normalApps.length,
    running: normalApps.filter((a) => toAppStatus(a.status) === "RUNNING").length,
    building: normalApps.filter((a) => toAppStatus(a.status) === "BUILDING").length,
    failed: normalApps.filter((a) => {
      const s = toAppStatus(a.status);
      return s === "FAILED" || s === "CRASHED";
    }).length,
    stopped: normalApps.filter((a) => {
      const s = toAppStatus(a.status);
      return s === "STOPPED" || s === "PENDING";
    }).length,
  };

  // Filter state
  const [filter, setFilter] = useState<StatFilter>("all");

  const filteredApps = normalApps.filter((app) => {
    const s = toAppStatus(app.status);
    switch (filter) {
      case "running":  return s === "RUNNING";
      case "building": return s === "BUILDING";
      case "failed":   return s === "FAILED" || s === "CRASHED";
      case "stopped":  return s === "STOPPED" || s === "PENDING";
      default:         return true; // "all"
    }
  });

  // Dynamic subtitle computation
  const runningCount = apps.filter((a) => toAppStatus(a.status) === "RUNNING").length;
  const needAttentionCount = events.filter(
    (e) => ATTENTION_TYPES.has(e.eventType) && e.status !== "IN_PROGRESS",
  ).length;

  const lastActivityAt = events.length > 0 ? new Date(events[0].createdAt) : null;
  const lastActivityStr = lastActivityAt
    ? formatDistanceToNow(lastActivityAt, { addSuffix: true })
    : null;

  const subtitle = [
    `${runningCount} running`,
    `${needAttentionCount} need attention`,
    lastActivityStr ? `Last activity ${lastActivityStr}` : null,
  ]
    .filter(Boolean)
    .join(". ");

  // UPDATE_AVAILABLE events for self-apps — only count if the event's SHA differs from
  // the app's current commitSha. Otherwise the update was already applied and the historical
  // event would keep the button visible forever.
  const updateAvailableEvents = selfApps
    .map((app) => {
      const ev = events.find(
        (e) => e.appName === app.appName && e.eventType === "UPDATE_AVAILABLE",
      );
      if (!ev || !ev.commitSha || ev.commitSha === app.commitSha) return null;
      return ev;
    })
    .filter((e): e is NonNullable<typeof e> => e !== null);

  // Skeleton rows while loading
  if (isLoading && apps.length === 0) {
    return (
      <div style={{ fontFamily: M.fontSans }}>
        <PageHeader
          kicker="WORKSPACE"
          title="Apps"
          subtitle="Loading…"
          actions={
            <>
              <Button variant="ghost" leadingIcon="search" size="sm" disabled>
                Search
              </Button>
              <Button variant="primary" leadingIcon="plus" size="sm" disabled>
                New app
              </Button>
            </>
          }
        />
        {/* Skeleton tiles */}
        <div style={{ display: "flex", gap: 12, marginBottom: 56 }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              style={{
                flex: 1,
                height: 88,
                borderRadius: M.rLg,
                background: M.surface,
                border: `1px solid ${M.line}`,
                opacity: 0.5,
              }}
            />
          ))}
        </div>
        {/* Skeleton rows */}
        <div style={{ display: "flex", flexDirection: "column", gap: 1 }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              style={{
                height: 52,
                background: M.surface,
                border: `1px solid ${M.line}`,
                borderRadius: i === 0 ? `${M.rLg}px ${M.rLg}px 0 0` : i === 4 ? `0 0 ${M.rLg}px ${M.rLg}px` : 0,
                opacity: 0.4,
              }}
            />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div style={{ fontFamily: M.fontSans }}>
      {/* F3.1 Header */}
      <PageHeader
        kicker="WORKSPACE"
        title="Apps"
        subtitle={subtitle}
        actions={
          <>
            <Button
              variant="ghost"
              leadingIcon="search"
              size="sm"
              onClick={() => commandPaletteStore.open()}
            >
              Search
              <span
                style={{
                  marginLeft: 8,
                  fontFamily: M.fontMono,
                  fontSize: 10.5,
                  color: M.fg3,
                  padding: "1px 5px",
                  border: `1px solid ${M.line}`,
                  borderRadius: 4,
                }}
              >
                ⌘K
              </span>
            </Button>
            <Link href="/setup" style={{ textDecoration: "none" }}>
              <Button variant="primary" leadingIcon="plus" size="sm">
                New app
              </Button>
            </Link>
          </>
        }
      />

      {/* F3.2 Stat tiles */}
      <StatTiles
        counts={counts}
        activeFilter={filter}
        onFilterChange={setFilter}
      />

      {/* F3.3 Self-apps section — only shown on "all" filter */}
      {filter === "all" && selfApps.length > 0 && (
        <SelfAppsSection
          apps={selfApps}
          onOpen={(name) => {
            const params = new URLSearchParams(searchParams.toString());
            params.set("app", name);
            router.push(`${pathname}?${params.toString()}`);
          }}
          updateAvailableEvents={updateAvailableEvents}
        />
      )}

      {/* F3.4 All-apps table */}
      <Section title="ALL APPS" count={filteredApps.length}>
        {apps.length === 0 ? (
          <div
            style={{
              padding: "60px 0",
              textAlign: "center",
              color: M.fg3,
              fontSize: 14,
            }}
          >
            No apps deployed yet. Push to a connected repo to trigger your first deploy.
          </div>
        ) : (
          <AppsTable apps={filteredApps} events={events} />
        )}
      </Section>

      {/* Drawer — driven by ?app= search param */}
      {openAppName && (
        <AppDetailDrawer appName={openAppName} onClose={handleDrawerClose} />
      )}
    </div>
  );
}

export default function Home() {
  return (
    <AppShell>
      <Suspense fallback={null}>
        <DashboardInner />
      </Suspense>
    </AppShell>
  );
}
