"use client";

import { createPortal } from "react-dom";
import { AlertTriangle, X } from "lucide-react";

interface Props {
  sha: string;
  onConfirm: () => void;
  onCancel: () => void;
  loading: boolean;
}

export function RollbackModal({ sha, onConfirm, onCancel, loading }: Props) {
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ background: "rgba(0,0,0,0.7)" }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        className="relative w-full max-w-md rounded-xl p-6 shadow-2xl"
        style={{
          background: "var(--c-surface-0, #0e0e14)",
          border: "1px solid var(--c-border-1)",
        }}
      >
        <button
          type="button"
          onClick={onCancel}
          className="absolute top-4 right-4 flex h-7 w-7 items-center justify-center rounded-md"
          style={{ color: "var(--c-fg-3)" }}
        >
          <X className="h-4 w-4" />
        </button>

        <div className="flex items-start gap-3 mb-4">
          <div
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
            style={{ background: "var(--c-status-building-bg)", border: "1px solid var(--c-status-building-line)" }}
          >
            <AlertTriangle className="h-5 w-5 text-amber-300" />
          </div>
          <div>
            <h2 className="text-[15px] font-semibold" style={{ color: "var(--c-fg-0)" }}>
              Roll back and pin?
            </h2>
            <p className="mt-1 text-[13px]" style={{ color: "var(--c-fg-2)" }}>
              Rolling back to commit{" "}
              <span
                className="font-mono rounded px-1"
                style={{ background: "var(--c-surface-2)", color: "var(--c-accent-fg)" }}
              >
                {sha.slice(0, 7)}
              </span>{" "}
              will also <strong style={{ color: "var(--c-fg-1)" }}>pin this app</strong> to that
              image. Future pushes to the deployed branch won&apos;t auto-deploy until you unpin
              the app.
            </p>
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 mt-6">
          <button
            type="button"
            onClick={onCancel}
            disabled={loading}
            className="rounded-md px-4 py-2 text-[13px] font-medium"
            style={{
              background: "var(--c-surface-2)",
              color: "var(--c-fg-1)",
              border: "1px solid var(--c-border-2)",
              cursor: loading ? "not-allowed" : "pointer",
              opacity: loading ? 0.5 : 1,
            }}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-md px-4 py-2 text-[13px] font-medium"
            style={{
              background: "var(--c-accent-soft)",
              color: "var(--c-accent-fg)",
              border: "1px solid var(--c-accent-line)",
              cursor: loading ? "not-allowed" : "pointer",
              opacity: loading ? 0.6 : 1,
            }}
          >
            {loading && (
              <span className="inline-block h-3.5 w-3.5 rounded-full border-2 border-current border-t-transparent animate-spin" />
            )}
            Roll back and pin
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
