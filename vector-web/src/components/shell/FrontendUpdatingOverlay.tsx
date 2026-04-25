"use client";

import { useUpdatingOverlay } from "@/hooks/useUpdatingOverlay";
import { UpdatingOverlayView } from "./UpdatingOverlayView";

export function FrontendUpdatingOverlay() {
  const view = useUpdatingOverlay({
    appName: "vector-web",
    probeUrl: "/favicon.ico",
    cacheKey: "vector:frontend-pending",
  });

  return (
    <UpdatingOverlayView
      view={view}
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
