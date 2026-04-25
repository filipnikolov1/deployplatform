"use client";

import useSWR from "swr";
import type { ContainerStats } from "@/types/launchpad";

const fetcher = async (url: string): Promise<ContainerStats> => {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to load stats: ${res.status}`);
  return res.json();
};

export function useContainerStats(appName: string): {
  stats: ContainerStats | null;
  isLoading: boolean;
} {
  const { data, isLoading } = useSWR<ContainerStats>(
    appName ? `/api/apps/${encodeURIComponent(appName)}/stats` : null,
    fetcher,
    {
      refreshInterval: 5_000,
      revalidateOnFocus: true,
      dedupingInterval: 2_000,
    },
  );

  return { stats: data ?? null, isLoading };
}
