"use client";

import useSWR from "swr";
import type { CommitsAhead } from "@/types/vector";

const fetcher = async (url: string): Promise<CommitsAhead> => {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to load commits-ahead: ${res.status}`);
  return res.json();
};

export function useCommitsAhead(appName: string): {
  commitsAhead: CommitsAhead;
  isLoading: boolean;
} {
  const { data, isLoading } = useSWR<CommitsAhead>(
    appName ? `/api/apps/${encodeURIComponent(appName)}/commits-ahead` : null,
    fetcher,
    {
      refreshInterval: 60_000,
      revalidateOnFocus: false,
      dedupingInterval: 10_000,
    },
  );

  return {
    commitsAhead: data ?? { count: null, commits: [], compareUrl: "#" },
    isLoading,
  };
}
