"use client";

import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/primitives/Button";
import { GlassCard } from "@/components/primitives/GlassCard";
import type { DeploymentEvent } from "@/types/launchpad";

interface Props {
  appName: string;
  event: DeploymentEvent;
  onCancel: () => void;
  onConfirm: () => void;
}

export function RollbackConfirmDialog({
  appName,
  event,
  onCancel,
  onConfirm,
}: Props) {
  const titleId = `rollback-confirm-${event.id}`;
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCancel();
    };
    document.addEventListener("keydown", onKey);
    const first = ref.current?.querySelector<HTMLElement>("button");
    first?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [onCancel]);

  if (typeof document === "undefined") return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        ref={ref}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-md"
      >
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
            <Button variant="ghost-purple" onClick={onCancel}>
              Cancel
            </Button>
            <button
              onClick={onConfirm}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-amber-500/20 border border-amber-500/40 text-amber-200 text-sm font-medium hover:bg-amber-500/30 transition-colors focus:outline-none focus-visible:ring-focus"
            >
              Confirm rollback
            </button>
          </div>
        </GlassCard>
      </div>
    </div>,
    document.body,
  );
}
