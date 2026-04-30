import useSWR from "swr";
import type { AppStats } from "@/types/analyzer";

const fetcher = async (url: string): Promise<AppStats> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export function useAppStats(appName: string) {
  const { data, error, isLoading } = useSWR<AppStats>(
    `/api/analyzer/apps/${encodeURIComponent(appName)}/stats`,
    fetcher,
    { revalidateOnFocus: false, dedupingInterval: 60_000 },
  );

  return { stats: data ?? null, isLoading, error };
}
