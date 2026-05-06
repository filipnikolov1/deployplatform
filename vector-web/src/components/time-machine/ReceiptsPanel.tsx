"use client";

import { FileText, GitCommit, Terminal } from "lucide-react";
import type { EvidenceItem } from "@/types/analyzer";

interface Props {
  evidence: EvidenceItem[];
  highlightId?: number | null;
  onJump?: (id: number) => void;
}

function summarize(item: EvidenceItem): string {
  if (item.type === "log") {
    const c = typeof item.content === "string" ? item.content : JSON.stringify(item.content);
    return c.length > 120 ? c.slice(0, 120) + "…" : c;
  }
  if (item.type === "diff") {
    return `Diff for suspect commit${item.suspectSha ? ` ${item.suspectSha.slice(0, 7)}` : ""}`;
  }
  if (item.type === "commit" && item.content && typeof item.content === "object") {
    const m = item.content as { message?: string; author?: string; sha?: string };
    const subject = (m.message ?? "").split("\n")[0];
    return `${m.sha ? m.sha.slice(0, 7) + " " : ""}${subject || "Commit metadata"}`;
  }
  return "Evidence";
}

function iconFor(type: EvidenceItem["type"]) {
  if (type === "log") return Terminal;
  if (type === "commit") return GitCommit;
  return FileText;
}

export function ReceiptsPanel({ evidence, highlightId, onJump }: Props) {
  return (
    <div
      className="rounded-xl overflow-hidden flex flex-col"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      <div
        className="flex items-center justify-between px-4 py-3"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
          Receipts
        </span>
        <span className="text-[11px] tabular-nums" style={{ color: "var(--c-fg-3)" }}>
          {evidence.length}
        </span>
      </div>
      <div className="overflow-y-auto" style={{ maxHeight: "420px" }}>
        {evidence.length === 0 ? (
          <div className="px-4 py-6 text-center text-[12px]" style={{ color: "var(--c-fg-3)" }}>
            No evidence captured.
          </div>
        ) : (
          evidence.map((item) => {
            const Icon = iconFor(item.type);
            const active = highlightId === item.id;
            return (
              <button
                key={item.id}
                type="button"
                id={`receipt-${item.id}`}
                onClick={() => onJump?.(item.id)}
                className="w-full text-left px-4 py-2 flex gap-3 transition-all hover:brightness-110 hover:translate-x-0.5"
                style={{
                  borderBottom: "1px solid var(--c-border-1)",
                  background: active ? "rgba(var(--c-accent-rgb,130,80,255),0.10)" : "transparent",
                }}
              >
                <span
                  className="inline-flex items-center justify-center h-5 w-5 rounded-full shrink-0 text-[11px] font-semibold tabular-nums"
                  style={{
                    background: "var(--c-accent-soft)",
                    color: "var(--c-accent-fg)",
                    border: "1px solid var(--c-accent-line)",
                  }}
                >
                  {item.id}
                </span>
                <Icon className="h-3.5 w-3.5 mt-1 shrink-0" style={{ color: "var(--c-fg-3)" }} />
                <div className="flex flex-col min-w-0 flex-1">
                  <span
                    className="text-[11px] uppercase tracking-[0.08em]"
                    style={{ color: "var(--c-fg-3)" }}
                  >
                    {item.type}
                    {item.source ? ` · ${item.source}` : ""}
                  </span>
                  <span className="text-[12px] truncate font-mono" style={{ color: "var(--c-fg-1)" }}>
                    {summarize(item)}
                  </span>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
