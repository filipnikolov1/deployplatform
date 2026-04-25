import useSWR from "swr";
import type { Deployment } from "@/types/deployment";

const fetcher = async (url: string): Promise<Deployment[]> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed to load apps: ${res.status}`);
  return res.json();
};

export function useApps() {
  const { data, error, isLoading, mutate } = useSWR<Deployment[]>(
    "/api/apps",
    fetcher,
    {
      refreshInterval: 10_000,
      revalidateOnFocus: true,
      dedupingInterval: 2_000,
    },
  );
  return {
    apps: data ?? [],
    isLoading,
    error,
    refresh: mutate,
  };
}
