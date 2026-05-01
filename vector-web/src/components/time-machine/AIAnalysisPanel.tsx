"use client";

import { Sparkles } from "lucide-react";

interface Props {
  narration: string | null;
}

export function AIAnalysisPanel({ narration }: Props) {
  return (
    <div
      className="rounded-xl overflow-hidden"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      <div
        className="flex items-center gap-2 px-4 py-3"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <Sparkles className="h-3.5 w-3.5" style={{ color: "var(--c-accent-fg)" }} />
        <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
          AI Analysis
        </span>
      </div>
      <div className="px-4 py-6 text-center text-[12px]" style={{ color: "var(--c-fg-3)" }}>
        {narration ?? "Analysis coming soon — narration will be added in a future update."}
      </div>
    </div>
  );
}
