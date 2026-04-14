"use client";

import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/primitives/Button";
import { GlassCard } from "@/components/primitives/GlassCard";
import { Modal } from "@/components/primitives/Modal";
import type { DeploymentEvent } from "@/types/launchpad";

interface Props {
  appName: string;
  event: DeploymentEvent;
  onCancel: () => void;
  onConfirm: () => void;
  submitting?: boolean;
}

export function RollbackConfirmDialog({
  appName,
  event,
  onCancel,
  onConfirm,
  submitting = false,
}: Props) {
  const titleId = `rollback-confirm-${event.id}`;

  return (
    <Modal open onClose={onCancel} labelledBy={titleId}>
      <div className="w-full max-w-md">
        <GlassCard radius="panel" className="p-6">
          <div className="flex items-start gap-3 mb-4">
            <div className="p-2 rounded-lg bg-amber-500/10 border border-amber-500/20">
              <AlertTriangle className="h-5 w-5 text-amber-400" />
            </div>
            <div className="flex-1">
              <h2
                id={titleId}
                className="text-base font-semibold text-slate-100 mb-1"
              >
                Roll back {appName}?
              </h2>
              <p className="text-sm text-slate-400">
                Roll back to commit{" "}
                <span className="font-mono text-slate-300">
                  {event.commitSha?.slice(0, 7) ?? "—"}
                </span>
                . Auto-deploy will be paused until you manually unpin.
              </p>
            </div>
          </div>
          <div className="flex items-center justify-end gap-2">
            <Button variant="ghost-purple" onClick={onCancel} disabled={submitting}>
              Cancel
            </Button>
            <button
              type="button"
              onClick={onConfirm}
              disabled={submitting}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-amber-500/20 border border-amber-500/40 text-amber-200 text-sm font-medium hover:bg-amber-500/30 transition-colors focus:outline-none focus-visible:ring-focus disabled:opacity-60"
            >
              {submitting ? "Rolling back…" : "Confirm rollback"}
            </button>
          </div>
        </GlassCard>
      </div>
    </Modal>
  );
}
