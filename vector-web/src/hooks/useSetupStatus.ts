"use client";

import useSWR from "swr";
import type { SetupStatus } from "@/types/vector";

const fetcher = async (url: string): Promise<SetupStatus> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed to load setup status: ${res.status}`);
  return res.json();
};

export function useSetupStatus() {
  const { data, isLoading } = useSWR<SetupStatus>(
    "/api/setup/status",
    fetcher,
    {
      refreshInterval: 15_000,
      revalidateOnFocus: true,
    },
  );

  return { status: data ?? null, isLoading };
}
