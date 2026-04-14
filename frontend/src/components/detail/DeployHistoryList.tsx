"use client";

import { useState } from "react";
import { useSWRConfig } from "swr";
import { RotateCcw, CheckCircle2, XCircle, Clock } from "lucide-react";
import type { DeploymentEvent } from "@/types/launchpad";
import { RollbackConfirmDialog } from "./RollbackConfirmDialog";
import { useToast } from "@/hooks/useToast";

interface Props {
  appName: string;
  events: DeploymentEvent[];
}

export function DeployHistoryList({ appName, events }: Props) {
  const toast = useToast();
  const { mutate } = useSWRConfig();
  const [rollbackTarget, setRollbackTarget] =
    useState<DeploymentEvent | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const performRollback = async (event: DeploymentEvent) => {
    setSubmitting(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(appName)}/rollback`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ eventId: event.id }),
        },
      );
      if (res.status === 409) {
        toast.error("App is busy — try again in a few seconds");
        return;
      }
      if (!res.ok) {
        toast.error("Rollback failed");
        return;
      }
      toast.success("Rolled back");
      void mutate("/api/apps");
      void mutate(`/api/apps/${encodeURIComponent(appName)}`);
      void mutate(
        (key) =>
          typeof key === "string" &&
          key.startsWith(`/api/apps/${encodeURIComponent(appName)}/events`),
      );
      setRollbackTarget(null);
    } catch {
      toast.error("Rollback failed");
    } finally {
      setSubmitting(false);
    }
  };

  const deploys = events
    .filter((e) =>
      ["DEPLOY_FINISHED", "MANUAL_ROLLBACK"].includes(e.eventType),
    )
    .slice(0, 10);

  if (deploys.length === 0) {
    return (
      <p className="text-sm text-slate-400">No deploy history yet.</p>
    );
  }

  return (
    <>
      <ul className="space-y-2">
        {deploys.map((event) => {
          const isSuccess = event.status === "SUCCESS";
          const Icon = isSuccess ? CheckCircle2 : XCircle;
          return (
            <li
              key={event.id}
              className="flex items-start gap-3 p-3 rounded-lg bg-white/[0.04] border border-white/[0.08]"
            >
              <Icon
                className={`h-4 w-4 mt-0.5 shrink-0 ${
                  isSuccess ? "text-emerald-400" : "text-red-400"
                }`}
              />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                  <span className="text-sm font-medium text-white truncate">
                    {event.commitMessage ?? "—"}
                  </span>
                  {event.commitSha && (
                    <span className="font-mono text-xs px-1.5 py-0.5 bg-white/5 rounded text-slate-300">
                      {event.commitSha.slice(0, 7)}
                    </span>
                  )}
                  {event.eventType === "MANUAL_ROLLBACK" && (
                    <span className="text-xs px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-300 border border-amber-500/20">
                      rollback
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-2 text-xs text-slate-400 tabular-nums">
                  <Clock className="h-3 w-3" />
                  <time dateTime={event.createdAt}>
                    {new Date(event.createdAt).toLocaleString()}
                  </time>
                  {event.durationMs != null && (
                    <span>· {(event.durationMs / 1000).toFixed(1)}s</span>
                  )}
                </div>
              </div>
              <button
                type="button"
                onClick={() => setRollbackTarget(event)}
                aria-label={`Roll back ${appName} to ${
                  event.commitSha?.slice(0, 7) ?? "this version"
                }`}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full border border-white/10 bg-white/[0.04] text-xs text-slate-200 hover:bg-white/[0.08] transition-colors focus:outline-none focus-visible:ring-focus"
              >
                <RotateCcw className="h-3 w-3" />
                Rollback
              </button>
            </li>
          );
        })}
      </ul>
      {rollbackTarget && (
        <RollbackConfirmDialog
          appName={appName}
          event={rollbackTarget}
          submitting={submitting}
          onCancel={() => setRollbackTarget(null)}
          onConfirm={() => performRollback(rollbackTarget)}
        />
      )}
    </>
  );
}
