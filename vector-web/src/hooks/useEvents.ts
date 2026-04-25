"use client";

import useSWR from "swr";
import type { DeploymentEvent } from "@/types/launchpad";

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
  const res = await fetch(url);
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

  const { data, error, isLoading, mutate } = useSWR<DeploymentEvent[]>(
    url,
    fetcher,
    {
      refreshInterval: 10_000,
      revalidateOnFocus: true,
      dedupingInterval: 2_000,
    },
  );

  return {
    events: data ?? [],
    isLoading,
    error: error ?? null,
    refresh: () => {
      void mutate();
    },
  };
}
