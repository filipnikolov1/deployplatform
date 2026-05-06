"use client";

import { useEffect, useRef, useState } from "react";
import { useUpdatingOverlay } from "@/hooks/useUpdatingOverlay";
import { useOperationProgress } from "@/hooks/useOperationProgress";
import { UpdatingOverlayView } from "./UpdatingOverlayView";

const GRACE_PERIOD_MS = 3_000;

export function FrontendUpdatingOverlay() {
  const view = useUpdatingOverlay({
    appName: "vector-web",
    probeUrl: "/favicon.ico",
    cacheKey: "vector:frontend-pending",
  });

  const progress = useOperationProgress(view.operationId);

  // Grace period: only use progress as primary source if frames arrive within 3s
  const [progressReady, setProgressReady] = useState(false);
  const graceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (progress.frames.length > 0 && !progressReady) {
      if (graceTimerRef.current) clearTimeout(graceTimerRef.current);
      setProgressReady(true);
    }
  }, [progress.frames.length, progressReady]);

  useEffect(() => {
    if (!view.operationId) {
      setProgressReady(false);
      return;
    }
    graceTimerRef.current = setTimeout(() => {
      // Grace period expired — polling fallback stays active
    }, GRACE_PERIOD_MS);
    return () => {
      if (graceTimerRef.current) clearTimeout(graceTimerRef.current);
    };
  }, [view.operationId]);

  const progressMessage = progressReady ? (progress.message ?? undefined) : undefined;
  const progressPercent = progressReady ? (progress.percent ?? undefined) : undefined;

  return (
    <UpdatingOverlayView
      view={view}
      progressMessage={progressMessage}
      progressPercent={progressPercent}
      copy={{
        statusBadge: "System Update",
        readyBadge: "Update Complete",
        waitingTitle: "Frontend is updating. Please wait…",
        readyTitle: "Frontend is back online.",
        waitingDescription:
          "Vector is restarting its frontend container. Keep this tab open — the dashboard will let you know when it's ready to refresh.",
        readyDescription:
          "The new frontend is serving. Refresh this page to load the updated bundle.",
        waitingFooter: "Waiting for frontend to come back online…",
        readyFooter: "Refresh is available.",
      }}
    />
  );
}
