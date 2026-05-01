"use client";

import { useEffect, useMemo, useState } from "react";
import useSWR from "swr";

interface FileCommit {
  sha: string;
  authorName?: string;
  authoredAt?: string;
  message?: string;
}

interface FileHistoryResponse {
  available: boolean;
  path?: string;
  commits?: FileCommit[];
  reason?: string;
}

const fetcher = async (url: string): Promise<FileHistoryResponse> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

interface Props {
  appName: string;
  filePath: string | null;
  initialSha: string | null;
  onSelect: (sha: string) => void;
}

export function ScrubFileHistory({ appName, filePath, initialSha, onSelect }: Props) {
  const key =
    filePath
      ? `/api/analyzer/apps/${encodeURIComponent(appName)}/commits/file-history?path=${encodeURIComponent(filePath)}&limit=20`
      : null;
  const { data, isLoading } = useSWR<FileHistoryResponse>(key, fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 60_000,
  });

  const commits = useMemo(() => data?.commits ?? [], [data]);
  const [index, setIndex] = useState(0);

  // When the commit list arrives, seed index to the suspect commit if present
  useEffect(() => {
    if (commits.length === 0) return;
    if (initialSha) {
      const idx = commits.findIndex((c) => c.sha === initialSha);
      if (idx >= 0) {
        setIndex(idx);
        return;
      }
    }
    setIndex(0);
  }, [commits, initialSha]);

  if (!filePath) return null;
  if (isLoading) {
    return (
      <div
        className="rounded-xl px-4 py-3 text-[12px]"
        style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)", color: "var(--c-fg-3)" }}
      >
        Loading file history…
      </div>
    );
  }
  if (!data?.available || commits.length === 0) {
    return (
      <div
        className="rounded-xl px-4 py-3 text-[12px]"
        style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)", color: "var(--c-fg-3)" }}
      >
        No history available for this file.
      </div>
    );
  }

  const active = commits[Math.min(index, commits.length - 1)];

  const handleChange = (next: number) => {
    setIndex(next);
    const c = commits[next];
    if (c) onSelect(c.sha);
  };

  return (
    <div
      className="rounded-xl px-4 py-3"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="text-[12px] font-medium" style={{ color: "var(--c-fg-1)" }}>
          File history
        </span>
        <span className="font-mono text-[11px]" style={{ color: "var(--c-fg-3)" }}>
          {commits.length} commits
        </span>
      </div>
      <input
        type="range"
        min={0}
        max={commits.length - 1}
        value={index}
        onChange={(e) => handleChange(Number(e.target.value))}
        className="w-full"
        style={{ accentColor: "var(--c-accent-fg)" }}
        aria-label="Scrub file history"
      />
      {active && (
        <div className="mt-2 flex flex-col gap-0.5">
          <div className="flex gap-2 items-center">
            <span
              className="font-mono text-[11px] rounded px-1.5 py-0.5"
              style={{ background: "var(--c-surface-2)", color: "var(--c-fg-2)" }}
            >
              {active.sha.slice(0, 7)}
            </span>
            {active.authorName && (
              <span className="text-[11px]" style={{ color: "var(--c-fg-3)" }}>
                {active.authorName}
              </span>
            )}
            {active.authoredAt && (
              <span className="text-[11px] tabular-nums" style={{ color: "var(--c-fg-3)" }}>
                {new Date(active.authoredAt).toLocaleDateString()}
              </span>
            )}
          </div>
          {active.message && (
            <div className="text-[12px] line-clamp-2" style={{ color: "var(--c-fg-2)" }}>
              {active.message.split("\n")[0]}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
