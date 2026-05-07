"use client";

import type { ReactNode } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";

interface PanelStateProps {
  title: string;
  message: string;
  action?: ReactNode;
  tone?: "neutral" | "warning" | "danger";
}

export function PanelState({ title, message, action, tone = "neutral" }: PanelStateProps) {
  const color =
    tone === "danger"
      ? "var(--c-status-failed-fg)"
      : tone === "warning"
      ? "var(--c-status-building-fg)"
      : "var(--c-fg-2)";

  return (
    <div className="px-6 py-8 text-center">
      <div
        className="mx-auto mb-2 flex h-8 w-8 items-center justify-center rounded-full"
        style={{
          background: tone === "neutral" ? "var(--c-surface-2)" : "var(--c-status-building-bg)",
          color,
        }}
      >
        <AlertTriangle className="h-4 w-4" />
      </div>
      <div className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
        {title}
      </div>
      <div className="mx-auto mt-1 max-w-md text-[12px] leading-5" style={{ color: "var(--c-fg-3)" }}>
        {message}
      </div>
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

export function LoadingPanelState({ label }: { label: string }) {
  return (
    <div className="flex min-h-32 items-center justify-center gap-2 px-6 py-8 text-[13px]" style={{ color: "var(--c-fg-3)" }}>
      <Loader2 className="h-4 w-4 animate-spin" />
      {label}
    </div>
  );
}
