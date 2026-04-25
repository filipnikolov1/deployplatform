"use client";

import { useEffect } from "react";
import useSWR from "swr";
import type { DeploymentEvent } from "@/types/vector";
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
    if (lastEventPayload !== null) {
      void mutate();
    }
  }, [lastEventPayload, mutate]);

  return {
    events: data ?? [],
    isLoading,
    error: error ?? null,
    refresh: () => {
      void mutate();
    },
  };
}
