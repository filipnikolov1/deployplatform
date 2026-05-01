import useSWR from "swr";
import type { RecentDeploy } from "@/types/analyzer";

const fetcher = async (url: string): Promise<RecentDeploy[]> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export function useRecentDeploys(appName: string) {
  const { data, error, isLoading, mutate } = useSWR<RecentDeploy[]>(
    `/api/analyzer/apps/${encodeURIComponent(appName)}/deploys`,
    fetcher,
    { revalidateOnFocus: false, dedupingInterval: 15_000 },
  );

  return { deploys: data ?? [], isLoading, error, refresh: mutate };
}
