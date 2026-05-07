import useSWR from "swr";

const fetcher = async <T>(url: string): Promise<T> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(10_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export interface UptimeResult {
  percent: number;
  uptimeMs: number;
  downtimeMs: number;
}

export interface AvgPullResult {
  avgMs: number;
  count: number;
}

export function useAppUptime(appName: string, days = 30) {
  const key = `/api/analyzer/apps/${encodeURIComponent(appName)}/uptime?days=${days}`;
  const { data, error, isLoading } = useSWR<UptimeResult>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 60_000,
  });
  return { uptime: data ?? null, isLoading, error };
}

export function useAvgPull(appName: string, days = 30) {
  const key = `/api/analyzer/apps/${encodeURIComponent(appName)}/avg-pull?days=${days}`;
  const { data, error, isLoading } = useSWR<AvgPullResult>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 60_000,
  });
  return { avgPull: data ?? null, isLoading, error };
}
