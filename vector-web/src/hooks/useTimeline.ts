import useSWR from "swr";
import type { TimelineEvent } from "@/types/analyzer";

const fetcher = async (url: string): Promise<TimelineEvent[]> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export function useTimeline(appName: string, from?: string, to?: string) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const qs = params.toString();
  const key = `/api/analyzer/apps/${encodeURIComponent(appName)}/timeline${qs ? `?${qs}` : ""}`;

  const { data, error, isLoading } = useSWR<TimelineEvent[]>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 30_000,
  });

  return { events: data ?? [], isLoading, error };
}
