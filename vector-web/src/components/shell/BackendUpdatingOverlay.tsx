"use client";

import { useEffect, useRef, useState } from "react";
import { useUpdatingOverlay } from "@/hooks/useUpdatingOverlay";
import { useOperationProgress } from "@/hooks/useOperationProgress";
import { UpdatingOverlayView } from "./UpdatingOverlayView";

const GRACE_PERIOD_MS = 3_000;

export function BackendUpdatingOverlay() {
  const view = useUpdatingOverlay({
    appName: "vector-api",
    probeUrl: "/api/apps",
    cacheKey: "vector:backend-pending",
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
        waitingTitle: "Backend is updating. Please wait…",
        readyTitle: "Backend is back online.",
        waitingDescription:
          "Vector is restarting its backend container. The dashboard will keep checking in the background until the update finishes.",
        readyDescription:
          "The API is responding again. Refresh this page to reconnect to the updated backend.",
        waitingFooter: "Waiting for backend to come back online…",
        readyFooter: "Refresh is available.",
      }}
    />
  );
}
