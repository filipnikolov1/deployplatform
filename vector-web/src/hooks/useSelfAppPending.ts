"use client";

import useSWR from "swr";

export type SelfUpdatePhase = "PULLING" | "RECREATING";

export interface PendingSelfUpdate {
  updateId: string;
  appName: string;
  phase: SelfUpdatePhase;
  targetSha: string;
  targetImage: string;
  triggeredAt: string;
}

const fetcher = async (url: string): Promise<PendingSelfUpdate | null> => {
  const res = await fetch(url, { cache: "no-store" });
  if (res.status === 204) return null;
  if (!res.ok) throw new Error(`Failed to load pending update: ${res.status}`);
  return res.json();
};

export function useSelfAppPending(appName: string | null | undefined) {
  const key = appName ? `/api/self-apps/${encodeURIComponent(appName)}/pending` : null;
  const { data, error, isLoading, mutate } = useSWR<PendingSelfUpdate | null>(
    key,
    fetcher,
    {
      refreshInterval: 2_000,
      revalidateOnFocus: true,
      dedupingInterval: 1_000,
    },
  );
  return {
    pending: data ?? null,
    isLoading,
    error,
    refresh: mutate,
  };
}
