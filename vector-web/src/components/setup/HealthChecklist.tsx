"use client";

import { CheckCircle2, XCircle } from "lucide-react";
import { useSetupStatus } from "@/hooks/useSetupStatus";

interface Row {
  key: "secretConfigured" | "dockerReachable" | "githubTokenConfigured";
  label: string;
  description: string;
}

const rows: Row[] = [
  {
    key: "secretConfigured",
    label: "Deploy-hook secret configured",
    description: "LAUNCHPAD_SECRET is set on the server.",
  },
  {
    key: "dockerReachable",
    label: "Docker daemon reachable",
    description: "Launchpad can pull images and run containers.",
  },
  {
    key: "githubTokenConfigured",
    label: "GitHub token configured",
    description: "Needed for commits-ahead comparisons.",
  },
];

export function HealthChecklist() {
  const { status, isLoading } = useSetupStatus();

  if (isLoading || !status) {
    return <div className="h-24 rounded-md skeleton-shimmer" style={{ background: "var(--c-surface-1)" }} />;
  }

  return (
    <ul className="flex flex-col gap-2 list-none p-0 m-0">
      {rows.map((row) => {
        const ok = status[row.key];
        return (
          <li
            key={row.key}
            className="flex items-start gap-3 rounded-md px-3.5 py-3"
            style={{
              background: "var(--c-surface-2)",
              border: "1px solid var(--c-border-1)",
            }}
          >
            {ok ? (
              <CheckCircle2 className="h-4 w-4 mt-0.5 shrink-0 text-green-400" aria-hidden />
            ) : (
              <XCircle className="h-4 w-4 mt-0.5 shrink-0 text-red-400" aria-hidden />
            )}
            <div className="flex-1 min-w-0">
              <div className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>{row.label}</div>
              <div className="text-xs mt-0.5" style={{ color: "var(--c-fg-3)" }}>{row.description}</div>
            </div>
            <span
              className="text-[10px] font-semibold uppercase tracking-[0.08em]"
              style={{ color: ok ? "#86EFAC" : "#FCA5A5" }}
            >
              {ok ? "OK" : "Action needed"}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
