"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import Link from "next/link";
import { useApps } from "@/hooks/useApps";
import { usePreferences } from "@/hooks/usePreferences";
import { AppCard } from "./AppCard";
import { AppCardSkeleton } from "./AppCardSkeleton";
import { AppDetailModal } from "@/components/detail/AppDetailModal";
import { StatusHero, type HeroFilter } from "./StatusHero";
import { EmptyState } from "./EmptyState";
import { Button } from "@/components/primitives/Button";
import { toAppStatus } from "@/lib/statusConfig";

export function AppGrid() {
  const { apps, isLoading, error } = useApps();
  const { prefs } = usePreferences();
  const [openAppName, setOpenAppName] = useState<string | null>(null);
  const [filter, setFilter] = useState<HeroFilter>("all");

  const filteredApps =
    apps?.filter((app) => {
      const status = toAppStatus(app.status);
      if (filter === "all") return true;
      if (filter === "running") return status === "RUNNING";
      if (filter === "building") return status === "BUILDING";
      if (filter === "failed") return status === "FAILED" || status === "CRASHED";
      return status === "STOPPED" || status === "PENDING";
    }) ?? [];

  const counts = {
    all: apps?.length ?? 0,
    running:
      apps?.filter((app) => toAppStatus(app.status) === "RUNNING").length ?? 0,
    building:
      apps?.filter((app) => toAppStatus(app.status) === "BUILDING").length ?? 0,
    failed:
      apps?.filter((app) => {
        const status = toAppStatus(app.status);
        return status === "FAILED" || status === "CRASHED";
      }).length ?? 0,
    stopped:
      apps?.filter((app) => {
        const status = toAppStatus(app.status);
        return status === "STOPPED" || status === "PENDING";
      }).length ?? 0,
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-end justify-between">
        <h1 className="text-2xl font-semibold text-slate-200">Apps</h1>
        <span className="font-mono text-sm text-slate-400 tabular-nums">
          {apps ? `${apps.length} total` : ""}
        </span>
      </div>
      <StatusHero activeFilter={filter} onFilterChange={setFilter} counts={counts} />

      {error && (
        <div role="alert" className="text-sm text-red-400">
          Failed to load apps. Will retry.
        </div>
      )}

      {isLoading && !apps ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <AppCardSkeleton key={i} />
          ))}
        </div>
      ) : filteredApps.length === 0 ? (
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
      ) : (
        <motion.div
          layout
          className={
            prefs.layout_mode === "list"
              ? "flex flex-col gap-4"
              : filteredApps.length <= 2
                ? "flex flex-wrap gap-6"
                : "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
          }
        >
          <AnimatePresence mode="popLayout">
            {filteredApps.map((app, index) => (
              <motion.div
                key={app.appName}
                layout
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ delay: index * 0.04, duration: 0.2 }}
                className={
                  prefs.layout_mode !== "list" && filteredApps.length <= 2
                    ? "w-full max-w-md"
                    : ""
                }
              >
                <AppCard
                  app={app}
                  onOpen={setOpenAppName}
                  mode={prefs.layout_mode}
                />
              </motion.div>
            ))}
          </AnimatePresence>
        </motion.div>
      )}

      {openAppName && (
        <AppDetailModal
          appName={openAppName}
          onClose={() => setOpenAppName(null)}
        />
      )}
    </div>
  );
}
