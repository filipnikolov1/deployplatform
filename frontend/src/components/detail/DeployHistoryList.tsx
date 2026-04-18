"use client";

import { useState } from "react";
import { useSWRConfig } from "swr";
import { RotateCcw, CheckCircle2, XCircle, Clock, HardDrive, Download } from "lucide-react";
import type { DeploymentEvent } from "@/types/launchpad";
import { RollbackConfirmDialog } from "./RollbackConfirmDialog";
import { useToast } from "@/hooks/useToast";

interface Props {
  appName: string;
  events: DeploymentEvent[];
  currentImage?: string | null;
}

export function DeployHistoryList({ appName, events, currentImage }: Props) {
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
    .filter(
      (e) =>
        ["DEPLOY_FINISHED", "MANUAL_ROLLBACK"].includes(e.eventType) &&
        e.status === "SUCCESS" &&
        !!e.imageName,
    )
    .slice(0, 10);

  if (deploys.length === 0) {
    return (
      <p className="text-sm text-slate-400">No deploy history yet.</p>
    );
  }

  return (
    <>
      <ul className="space-y-3">
        {deploys.map((event) => {
          const isSuccess = event.status === "SUCCESS";
          const Icon = isSuccess ? CheckCircle2 : XCircle;
          const isCurrent = !!currentImage && event.imageName === currentImage;
          const local = event.availableLocally === true;
          const remote = event.availableLocally === false;
          return (
            <li
              key={event.id}
              className="overflow-hidden rounded-card border border-white/[0.08] bg-black/35 shadow-[0_18px_40px_rgba(0,0,0,0.35),inset_0_1px_0_rgba(255,255,255,0.04)]"
            >
              <div className="flex items-start gap-4 px-5 py-5 sm:px-6">
                <div
                  className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border shadow-[inset_0_1px_0_rgba(255,255,255,0.05)] ${
                    isSuccess
                      ? "border-emerald-400/30 bg-emerald-500/14 text-emerald-200"
                      : "border-red-400/25 bg-red-500/12 text-red-200"
                  }`}
                >
                  <Icon className="h-4 w-4" />
                </div>
                <div className="min-w-0 flex-1 pt-1">
                  <div className="mb-1 flex items-start justify-between gap-4">
                    <div className="flex flex-wrap items-center gap-3">
                      <span
                        className="line-clamp-2 break-words text-sm font-medium text-white"
                        title={event.commitMessage ?? undefined}
                      >
                        {event.commitMessage ?? "—"}
                      </span>
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.16em] ${
                          isSuccess
                            ? "border-emerald-400/20 bg-emerald-500/10 text-emerald-200"
                            : "border-red-400/20 bg-red-500/10 text-red-200"
                        }`}
                      >
                        <Icon className="h-3 w-3" />
                        {isSuccess ? "Success" : "Failed"}
                      </span>
                      {event.eventType === "MANUAL_ROLLBACK" && (
                        <span className="inline-flex items-center rounded-full border border-amber-300/20 bg-amber-500/10 px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.16em] text-amber-100">
                          rollback
                        </span>
                      )}
                      {isCurrent && (
                        <span className="inline-flex items-center rounded-full border border-sky-300/20 bg-sky-500/10 px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.16em] text-sky-100">
                          current
                        </span>
                      )}
                    </div>
                    {!isCurrent && (
                      <div className="flex shrink-0 items-center gap-2">
                        {local && (
                          <span
                            className="inline-flex items-center gap-1 rounded-full border border-emerald-400/20 bg-emerald-500/10 px-2 py-1 text-[11px] font-medium text-emerald-200"
                            title="Image is cached locally — rollback is instant"
                          >
                            <HardDrive className="h-3 w-3" />
                            available locally
                          </span>
                        )}
                        {remote && (
                          <span
                            className="inline-flex items-center gap-1 rounded-full border border-amber-300/20 bg-amber-500/10 px-2 py-1 text-[11px] font-medium text-amber-100"
                            title="Image not cached locally — rollback will re-pull from the registry"
                          >
                            <Download className="h-3 w-3" />
                            requires re-pull
                          </span>
                        )}
                        <button
                          type="button"
                          onClick={() => setRollbackTarget(event)}
                          aria-label={`Roll back ${appName} to ${
                            event.commitSha?.slice(0, 7) ?? "this version"
                          }`}
                          className="inline-flex items-center gap-1 rounded-full border border-white/[0.08] bg-black/30 px-3 py-1.5 text-xs text-slate-200 transition-colors hover:bg-black/45 focus:outline-none focus-visible:ring-focus"
                        >
                          <RotateCcw className="h-3 w-3" />
                          Rollback
                        </button>
                      </div>
                    )}
                  </div>
                  {event.commitSha && (
                    <span className="mb-2 inline-flex rounded-md border border-white/[0.08] bg-black/30 px-2 py-1 font-mono text-xs text-slate-300">
                      {event.commitSha.slice(0, 7)}
                    </span>
                  )}
                  <div className="flex flex-wrap items-center gap-2 text-xs tabular-nums text-slate-400">
                    <Clock className="h-3 w-3" />
                    <time dateTime={event.createdAt}>
                      {new Date(event.createdAt).toLocaleString()}
                    </time>
                    {event.durationMs != null && (
                      <span>· {(event.durationMs / 1000).toFixed(1)}s</span>
                    )}
                  </div>
                </div>
              </div>
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
