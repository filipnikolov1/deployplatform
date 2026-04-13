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
    return (
      <div className="h-24 rounded-lg bg-white/[0.02] animate-pulse" />
    );
  }

  return (
    <ul className="space-y-2">
      {rows.map((row) => {
        const ok = status[row.key];
        const Icon = ok ? CheckCircle2 : XCircle;
        return (
          <li
            key={row.key}
            className="flex items-start gap-3 p-3 rounded-lg bg-white/[0.04] border border-white/[0.08]"
          >
            <Icon
              className={`h-5 w-5 mt-0.5 shrink-0 ${
                ok ? "text-emerald-400" : "text-red-400"
              }`}
              aria-hidden="true"
            />
            <div className="flex-1">
              <div className="text-sm font-medium text-slate-100">
                {row.label}
              </div>
              <div className="text-xs text-slate-400 mt-0.5">
                {row.description}
              </div>
            </div>
            <span className="sr-only">{ok ? "OK" : "Not configured"}</span>
          </li>
        );
      })}
    </ul>
  );
}
