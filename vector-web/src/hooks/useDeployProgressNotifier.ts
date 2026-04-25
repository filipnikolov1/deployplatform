"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useEvents } from "@/hooks/useEvents";
import { useToast } from "@/hooks/useToast";
import type { DeploymentEvent, DeploymentEventType } from "@/types/vector";

const DEPLOY_EVENT_TYPES: readonly DeploymentEventType[] = [
  "DEPLOY_TRIGGERED",
  "DEPLOY_STARTED",
  "DEPLOY_FINISHED",
  "FAILED",
] as const;

function isDeployEvent(e: DeploymentEvent): boolean {
  return DEPLOY_EVENT_TYPES.includes(e.eventType);
}

function formatDuration(ms: number | null): string | undefined {
  if (ms == null || ms <= 0) return undefined;
  if (ms < 1000) return `in ${ms}ms`;
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `in ${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const rem = seconds % 60;
  return rem === 0 ? `in ${minutes}m` : `in ${minutes}m ${rem}s`;
}

interface TrackedToast {
  toastId: number;
  lastEventId: number;
}

export function useDeployProgressNotifier() {
  const { events } = useEvents({ limit: 30 });
  const toast = useToast();
  const router = useRouter();
  const trackedRef = useRef<Map<string, TrackedToast>>(new Map());
  const seededRef = useRef(false);
  const seenResolvedRef = useRef<Set<number>>(new Set());

  useEffect(() => {
    if (events.length === 0) return;

    const latestByApp = new Map<string, DeploymentEvent>();
    for (const e of events) {
      if (!isDeployEvent(e)) continue;
      const existing = latestByApp.get(e.appName);
      if (!existing || new Date(e.createdAt).getTime() > new Date(existing.createdAt).getTime()) {
        latestByApp.set(e.appName, e);
      }
    }

    const latestEntries = Array.from(latestByApp.entries());

    if (!seededRef.current) {
      for (const [appName, evt] of latestEntries) {
        if (evt.status !== "IN_PROGRESS") {
          seenResolvedRef.current.add(evt.id);
          trackedRef.current.delete(appName);
        }
      }
      seededRef.current = true;
      return;
    }

    for (const [appName, evt] of latestEntries) {
      const tracked = trackedRef.current.get(appName);

      if (evt.status === "IN_PROGRESS") {
        if (tracked) continue;
        const toastId = toast.progress(appName, {
          sublabel: "Deploying…",
          persistent: true,
          action: {
            label: "View",
            onClick: () => router.push(`/?app=${encodeURIComponent(appName)}`),
          },
        });
        trackedRef.current.set(appName, { toastId, lastEventId: evt.id });
        continue;
      }

      if (evt.status === "SUCCESS" || evt.status === "FAILURE") {
        if (!tracked) {
          seenResolvedRef.current.add(evt.id);
          continue;
        }
        if (seenResolvedRef.current.has(evt.id)) continue;
        if (evt.id === tracked.lastEventId) continue;

        const duration = formatDuration(evt.durationMs);
        if (evt.status === "SUCCESS") {
          toast.update(tracked.toastId, {
            variant: "success",
            message: `Deployed ${appName}`,
            sublabel: duration,
            persistent: false,
            durationMs: 5000,
          });
        } else {
          const reason = evt.errorMessage?.trim();
          toast.update(tracked.toastId, {
            variant: "error",
            message: reason ? `Deploy failed: ${reason}` : `Deploy failed: ${appName}`,
            sublabel: duration,
            persistent: false,
            durationMs: 6000,
          });
        }
        seenResolvedRef.current.add(evt.id);
        trackedRef.current.delete(appName);
      }
    }
  }, [events, toast, router]);
}
