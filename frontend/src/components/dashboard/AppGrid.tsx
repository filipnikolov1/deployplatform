"use client";

import { useState } from "react";
import Link from "next/link";
import { useApps } from "@/hooks/useApps";
import { AppDetailDrawer } from "@/components/detail/AppDetailDrawer";
import { StatusHero, type HeroFilter } from "./StatusHero";
import { SelfAppsBand } from "./SelfAppsBand";
import { AppTable } from "./AppTable";
import { EmptyState } from "./EmptyState";
import { Button } from "@/components/primitives/Button";
import { toAppStatus } from "@/lib/statusConfig";

export function AppGrid() {
  const { apps, isLoading, error } = useApps();
  const [openAppName, setOpenAppName] = useState<string | null>(null);
  const [filter, setFilter] = useState<HeroFilter>("all");

  const selfApps = apps?.filter((a) => a.isSelfApp) ?? [];
  const regularApps = apps?.filter((a) => !a.isSelfApp) ?? [];

  const filteredApps =
    apps?.filter((app) => {
      const status = toAppStatus(app.status);
      if (filter === "all") return !app.isSelfApp; // self-apps shown in band, not table when "all"
      if (filter === "running") return status === "RUNNING";
      if (filter === "building") return status === "BUILDING";
      if (filter === "failed") return status === "FAILED" || status === "CRASHED";
      return status === "STOPPED" || status === "PENDING";
    }) ?? [];

  const counts = {
    all: apps?.length ?? 0,
    running: apps?.filter((a) => toAppStatus(a.status) === "RUNNING").length ?? 0,
    building: apps?.filter((a) => toAppStatus(a.status) === "BUILDING").length ?? 0,
    failed: apps?.filter((a) => {
      const s = toAppStatus(a.status);
      return s === "FAILED" || s === "CRASHED";
    }).length ?? 0,
    stopped: apps?.filter((a) => {
      const s = toAppStatus(a.status);
      return s === "STOPPED" || s === "PENDING";
    }).length ?? 0,
  };

  return (
    <div className="flex flex-col">
      {/* Page header */}
      <div
        className="flex items-end justify-between gap-4 pb-5 mb-6 flex-wrap"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <div>
          <h1
            className="text-2xl font-semibold tracking-[-0.01em] m-0"
            style={{ color: "var(--c-fg-0)", lineHeight: 1.2 }}
          >
            Apps
          </h1>
          <p className="mt-1.5 text-[13px]" style={{ color: "var(--c-fg-2)" }}>
            Live deployment status, actions, and rollout visibility.
          </p>
        </div>
        {apps && (
          <span
            className="font-mono text-sm tabular-nums"
            style={{ color: "var(--c-fg-3)" }}
          >
            {apps.length} total
          </span>
        )}
      </div>

      <StatusHero activeFilter={filter} onFilterChange={setFilter} counts={counts} />

      {error && (
        <div role="alert" className="mb-4 text-sm text-red-400">
          Failed to load apps. Will retry.
        </div>
      )}

      {isLoading && !apps ? (
        /* Loading skeleton */
        <div className="flex flex-col gap-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              className="h-12 rounded-[10px] skeleton-shimmer"
              style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
            />
          ))}
        </div>
      ) : !apps || apps.length === 0 ? (
        <EmptyState>
          <EmptyState.Media />
          <EmptyState.Title>No apps deployed yet</EmptyState.Title>
          <EmptyState.Description>
            Push to a connected repo to trigger your first deploy.
          </EmptyState.Description>
          <EmptyState.Actions>
            <Link href="/setup">
              <Button>View setup guide</Button>
            </Link>
          </EmptyState.Actions>
        </EmptyState>
      ) : filter === "all" ? (
        /* Default view: self-apps band + regular apps table */
        <>
          {selfApps.length > 0 && (
            <SelfAppsBand apps={selfApps} onOpen={setOpenAppName} />
          )}
          <AppTable apps={regularApps} onOpen={setOpenAppName} />
        </>
      ) : (
        /* Filtered view: show all matching as table */
        filteredApps.length === 0 ? (
          <div
            className="py-10 text-center text-sm rounded-[14px]"
            style={{
              color: "var(--c-fg-3)",
              background: "var(--c-surface-1)",
              border: "1px solid var(--c-border-1)",
            }}
          >
            No apps match this filter.
          </div>
        ) : (
          <AppTable apps={filteredApps} onOpen={setOpenAppName} />
        )
      )}

      {openAppName && (
        <AppDetailDrawer
          appName={openAppName}
          onClose={() => setOpenAppName(null)}
        />
      )}
    </div>
  );
}
