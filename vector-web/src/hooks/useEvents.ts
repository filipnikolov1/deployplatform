"use client";

import { useEffect } from "react";
import useSWR from "swr";
import type { DeploymentEvent, PlatformEventEnvelope } from "@/types/vector";
import { useEventStream } from "@/hooks/useEventStream";

interface UseEventsArgs {
  appName?: string;
  limit?: number;
}

interface UseEventsResult {
  events: DeploymentEvent[];
  isLoading: boolean;
  error: Error | null;
  refresh: () => void;
}

const fetcher = async (url: string): Promise<DeploymentEvent[]> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed to load events: ${res.status}`);
  return res.json();
};

function eventFromEnvelope(envelope: PlatformEventEnvelope): DeploymentEvent {
  return {
    id: envelope.id,
    appName: envelope.appName,
    operationId: envelope.operationId,
    eventType: envelope.type,
    status: envelope.status,
    imageName: envelope.payload.imageName,
    branch: envelope.payload.branch,
    commitSha: envelope.payload.commitSha,
    commitMessage: envelope.payload.commitMessage,
    commitAuthor: envelope.payload.commitAuthor,
    durationMs: envelope.payload.durationMs,
    errorMessage: envelope.payload.errorMessage,
    triggeredBy: envelope.payload.triggeredBy ?? "AUTOMATIC",
    rollbackFromSha: envelope.payload.rollbackFromSha,
    createdAt: envelope.occurredAt,
    finishedAt: envelope.status === "IN_PROGRESS" ? null : envelope.occurredAt,
    availableLocally: envelope.payload.availableLocally,
  };
}

export function useEvents({
  appName,
  limit = 50,
}: UseEventsArgs = {}): UseEventsResult {
  const url = appName
    ? `/api/apps/${encodeURIComponent(appName)}/events?limit=${limit}`
    : `/api/events?limit=${limit}`;

  const { sseConnected, lastEventPayload } = useEventStream();

  const { data, error, isLoading, mutate } = useSWR<DeploymentEvent[]>(
    url,
    fetcher,
    {
      refreshInterval: sseConnected ? 0 : 10_000,
      revalidateOnFocus: true,
      dedupingInterval: 2_000,
    },
  );

  useEffect(() => {
    if (lastEventPayload === null) return;
    try {
      const envelope = JSON.parse(lastEventPayload) as PlatformEventEnvelope;
      if (envelope.version !== 1) return;
      if (appName && envelope.appName !== appName) return;
      const liveEvent = eventFromEnvelope(envelope);
      void mutate((current) => {
        const existing = current ?? [];
        const withoutDuplicate = existing.filter((event) => event.id !== liveEvent.id);
        return [liveEvent, ...withoutDuplicate].slice(0, limit);
      }, { revalidate: false });
    } catch {
      void mutate();
    }
  }, [appName, lastEventPayload, limit, mutate]);

  return {
    events: data ?? [],
    isLoading,
    error: error ?? null,
    refresh: () => {
      void mutate();
    },
  };
}
