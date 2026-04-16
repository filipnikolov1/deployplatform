"use client";

import { ArrowUpCircle, RotateCcw, Square, X } from "lucide-react";
import { Button } from "@/components/primitives/Button";
import { StatusDot } from "@/components/primitives/StatusDot";
import type { Deployment } from "@/types/deployment";

interface Props {
  app: Deployment;
  onClose: () => void;
  onRestart: () => void;
  onStop: () => void;
  onUpdate?: () => void;
  restarting?: boolean;
  updating?: boolean;
  titleId: string;
}

export function AppDetailHeader({
  app,
  onClose,
  onRestart,
  onStop,
  onUpdate,
  restarting = false,
  updating = false,
  titleId,
}: Props) {
  const hasUpdate =
    app.isSelfApp &&
    !!app.latestKnownSha &&
    app.commitSha !== app.latestKnownSha;
  const canRestart = app.status !== "STOPPED" && app.status !== "PENDING";
  const canStop = app.status !== "STOPPED";
  const statusLabel = app.status.toLowerCase().replace("_", " ");

  return (
    <div className="flex items-center justify-between px-6 py-5 border-b border-white/[0.08]">
      <div className="flex items-center gap-3">
        <StatusDot status={app.status} />
        <div>
          <h2 id={titleId} className="text-lg font-semibold text-slate-100">
            {app.appName}
          </h2>
          <span className="text-[11px] uppercase tracking-[0.14em] text-slate-400">
            {statusLabel}
          </span>
        </div>
      </div>
      <div className="flex items-center gap-2">
        {hasUpdate && onUpdate && (
          <Button
            variant="solid-amber"
            onClick={onUpdate}
            disabled={updating}
            loading={updating}
            leadingIcon={<ArrowUpCircle size={16} />}
          >
            {updating ? "Updating…" : "Update"}
          </Button>
        )}
        <Button
          variant="ghost-purple"
          onClick={onRestart}
          disabled={!canRestart || restarting}
          loading={restarting}
          leadingIcon={<RotateCcw size={16} />}
        >
          Restart
        </Button>
        <Button
          variant="ghost-red"
          onClick={onStop}
          disabled={!canStop}
          leadingIcon={<Square size={16} />}
        >
          Stop
        </Button>
        <Button
          variant="icon"
          aria-label="Close"
          onClick={onClose}
          className="h-11 w-11 md:h-8 md:w-8 rounded-lg hover:bg-white/10"
        >
          <X size={20} />
        </Button>
      </div>
    </div>
  );
}
