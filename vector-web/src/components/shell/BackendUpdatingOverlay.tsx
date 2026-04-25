"use client";

import { useUpdatingOverlay } from "@/hooks/useUpdatingOverlay";
import { UpdatingOverlayView } from "./UpdatingOverlayView";

export function BackendUpdatingOverlay() {
  const view = useUpdatingOverlay({
    appName: "launchpad-backend",
    probeUrl: "/api/apps",
    cacheKey: "launchpad:backend-pending",
  });

  return (
    <UpdatingOverlayView
      view={view}
      copy={{
        statusBadge: "System Update",
        readyBadge: "Update Complete",
        waitingTitle: "Backend is updating. Please wait…",
        readyTitle: "Backend is back online.",
        waitingDescription:
          "Launchpad is restarting its backend container. The dashboard will keep checking in the background until the update finishes.",
        readyDescription:
          "The API is responding again. Refresh this page to reconnect to the updated backend.",
        waitingFooter: "Waiting for backend to come back online…",
        readyFooter: "Refresh is available.",
      }}
    />
  );
}
