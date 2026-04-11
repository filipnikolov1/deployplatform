"use client";

import { useState } from "react";
import { useApps } from "@/hooks/useApps";
import { AppCard } from "./AppCard";
import { AppCardSkeleton } from "./AppCardSkeleton";
import { AppDetailModal } from "@/components/detail/AppDetailModal";

export function AppGrid() {
  const { apps, isLoading, error } = useApps();
  const [openAppName, setOpenAppName] = useState<string | null>(null);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-end justify-between">
        <h1 className="text-2xl font-semibold text-slate-200">Apps</h1>
        <span className="font-mono text-sm text-slate-400 tabular-nums">
          {apps ? `${apps.length} total` : ""}
        </span>
      </div>

      {error && (
        <div role="alert" className="text-sm text-red-400">
          Failed to load apps. Will retry.
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {isLoading && !apps &&
          Array.from({ length: 6 }).map((_, i) => <AppCardSkeleton key={i} />)}
        {apps?.map((app) => (
          <AppCard key={app.appName} app={app} onOpen={setOpenAppName} />
        ))}
      </div>

      {openAppName && (
        <AppDetailModal
          appName={openAppName}
          onClose={() => setOpenAppName(null)}
        />
      )}
    </div>
  );
}
