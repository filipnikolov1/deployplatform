"use client";

import { ReactNode, useMemo } from "react";
import { Loader2, RefreshCw, Sparkles } from "lucide-react";
import ReactMarkdown from "react-markdown";
import type { AiNarrationStatus } from "@/types/analyzer";

interface Props {
  narration: string | null;
  status: AiNarrationStatus;
  failureReason: string | null;
  providerUsed: string | null;
  regenerateCount: number;
  regenerateLimit: number;
  onJumpToReceipt: (id: number) => void;
  onRegenerate: () => void;
  isRegenerating: boolean;
}

const CITATION_RX = /\[(\d+)\]/g;

/**
 * Splits a string into renderable nodes, turning every "[N]" token into a
 * clickable citation chip that jumps to the matching receipt.
 */
function renderTextWithCitations(text: string, onJump: (id: number) => void): ReactNode[] {
  const nodes: ReactNode[] = [];
  let lastIdx = 0;
  let match: RegExpExecArray | null;
  let key = 0;
  while ((match = CITATION_RX.exec(text)) !== null) {
    if (match.index > lastIdx) {
      nodes.push(text.slice(lastIdx, match.index));
    }
    const id = Number(match[1]);
    nodes.push(
      <button
        key={`cite-${key++}-${match.index}`}
        type="button"
        onClick={() => onJump(id)}
        className="inline-flex items-center justify-center align-baseline mx-[1px] px-[5px] h-[18px] rounded-full text-[10px] font-semibold tabular-nums transition-[filter] hover:brightness-110"
        style={{
          background: "var(--c-accent-soft)",
          color: "var(--c-accent-fg)",
          border: "1px solid var(--c-accent-line)",
        }}
        aria-label={`Jump to receipt ${id}`}
      >
        {id}
      </button>,
    );
    lastIdx = match.index + match[0].length;
  }
  if (lastIdx < text.length) nodes.push(text.slice(lastIdx));
  return nodes;
}

export function AIAnalysisPanel({
  narration,
  status,
  failureReason,
  providerUsed,
  regenerateCount,
  regenerateLimit,
  onJumpToReceipt,
  onRegenerate,
  isRegenerating,
}: Props) {
  const canRegenerate = status === "UNAVAILABLE" && regenerateCount < regenerateLimit;

  // Memoise the markdown component config so each paragraph gets the citation
  // post-processor without React having to rebuild the tree on every render.
  const components = useMemo(
    () => ({
      p: ({ children }: { children?: ReactNode }) => (
        <p className="mb-3 last:mb-0 text-[13px] leading-[1.55]" style={{ color: "var(--c-fg-1)" }}>
          {processChildren(children, onJumpToReceipt)}
        </p>
      ),
      strong: ({ children }: { children?: ReactNode }) => (
        <strong style={{ color: "var(--c-fg-1)" }}>{processChildren(children, onJumpToReceipt)}</strong>
      ),
      em: ({ children }: { children?: ReactNode }) => (
        <em style={{ color: "var(--c-fg-2)" }}>{processChildren(children, onJumpToReceipt)}</em>
      ),
      code: ({ children }: { children?: ReactNode }) => (
        <code
          className="font-mono text-[12px] px-1 py-0.5 rounded"
          style={{ background: "var(--c-surface-2)", color: "var(--c-fg-1)" }}
        >
          {children}
        </code>
      ),
      li: ({ children }: { children?: ReactNode }) => (
        <li className="ml-5 list-disc text-[13px] leading-[1.55]" style={{ color: "var(--c-fg-1)" }}>
          {processChildren(children, onJumpToReceipt)}
        </li>
      ),
    }),
    [onJumpToReceipt],
  );

  return (
    <div
      className="rounded-xl overflow-hidden"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      <div
        className="flex items-center justify-between px-4 py-3"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <div className="flex items-center gap-2">
          <Sparkles className="h-3.5 w-3.5" style={{ color: "var(--c-accent-fg)" }} />
          <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
            AI Analysis
          </span>
          {providerUsed && status === "AVAILABLE" && (
            <span className="text-[10px] uppercase tracking-[0.08em]" style={{ color: "var(--c-fg-3)" }}>
              · {providerUsed}
            </span>
          )}
        </div>
        {canRegenerate && (
          <button
            type="button"
            onClick={onRegenerate}
            disabled={isRegenerating}
            className="inline-flex items-center gap-1 text-[11px] px-2 py-1 rounded-md transition-[filter] hover:brightness-110 disabled:opacity-50"
            style={{
              background: "var(--c-surface-2)",
              color: "var(--c-fg-2)",
              border: "1px solid var(--c-border-2)",
            }}
          >
            {isRegenerating ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <RefreshCw className="h-3 w-3" />
            )}
            Regenerate
          </button>
        )}
      </div>
      <div className="px-4 py-4">
        {status === "PENDING" && (
          <div
            className="flex items-center gap-2 text-[12px] py-3"
            style={{ color: "var(--c-fg-3)" }}
          >
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            Generating analysis…
          </div>
        )}
        {status === "UNAVAILABLE" && (
          <div className="text-[12px]" style={{ color: "var(--c-fg-3)" }}>
            <p className="mb-2">Analysis unavailable.</p>
            {failureReason && (
              <p className="font-mono text-[11px]" style={{ color: "var(--c-fg-3)" }}>
                {failureReason}
              </p>
            )}
            {!canRegenerate && regenerateCount >= regenerateLimit && (
              <p className="mt-2" style={{ color: "var(--c-fg-3)" }}>
                Regenerate limit reached for this crash.
              </p>
            )}
          </div>
        )}
        {status === "AVAILABLE" && narration && (
          <ReactMarkdown components={components}>{narration}</ReactMarkdown>
        )}
      </div>
    </div>
  );
}

/**
 * react-markdown gives us children as a `ReactNode` (string, element, or array).
 * We walk the tree and replace every plain-string segment's "[N]" tokens with a
 * citation chip. Anything that's not a string passes through untouched, so
 * styled spans (strong/em/code) keep their formatting.
 */
function processChildren(children: ReactNode, onJump: (id: number) => void): ReactNode {
  if (typeof children === "string") {
    return renderTextWithCitations(children, onJump);
  }
  if (Array.isArray(children)) {
    return children.map((c, i) =>
      typeof c === "string" ? (
        <span key={`s-${i}`}>{renderTextWithCitations(c, onJump)}</span>
      ) : (
        c
      ),
    );
  }
  return children;
}
