import useSWR from "swr";
import type { CommitDetail, LogEntry, CommitDiff, CommitFile } from "@/types/analyzer";

const fetcher = async <T>(url: string): Promise<T> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export function useCommitDetail(appName: string, sha: string | null) {
  const key = sha
    ? `/api/analyzer/apps/${encodeURIComponent(appName)}/commits/${encodeURIComponent(sha)}`
    : null;
  const { data, error, isLoading } = useSWR<CommitDetail>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 60_000,
  });
  return { commit: data ?? null, isLoading, error };
}

export function useCommitLogs(appName: string, sha: string | null) {
  const key = sha
    ? `/api/analyzer/apps/${encodeURIComponent(appName)}/commits/${encodeURIComponent(sha)}/logs`
    : null;
  const { data, error, isLoading } = useSWR<LogEntry[]>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 60_000,
  });
  return { logs: data ?? [], isLoading, error };
}

export function useCommitDiff(appName: string, sha: string | null) {
  const key = sha
    ? `/api/analyzer/apps/${encodeURIComponent(appName)}/commits/${encodeURIComponent(sha)}/diff`
    : null;
  const { data, error, isLoading } = useSWR<CommitDiff>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 30_000,
  });
  return { diff: data ?? null, isLoading, error };
}

export function useFileAtCommit(
  appName: string,
  sha: string | null,
  path: string | null,
) {
  const key =
    sha && path
      ? `/api/analyzer/apps/${encodeURIComponent(appName)}/commits/${encodeURIComponent(sha)}/files?path=${encodeURIComponent(path)}`
      : null;
  const { data, error, isLoading } = useSWR<CommitFile>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 300_000,
  });
  return { file: data ?? null, isLoading, error };
}
