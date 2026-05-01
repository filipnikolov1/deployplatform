import useSWR from "swr";
import type { CrashAnalysis } from "@/types/analyzer";

const fetcher = async <T>(url: string): Promise<T> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(20_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export function useCrashAnalysis(appName: string, crashId: string | null) {
  const key = crashId
    ? `/api/analyzer/apps/${encodeURIComponent(appName)}/crashes/${encodeURIComponent(crashId)}`
    : null;
  const { data, error, isLoading, mutate } = useSWR<CrashAnalysis>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 30_000,
  });
  return { analysis: data ?? null, isLoading, error, refresh: mutate };
}
