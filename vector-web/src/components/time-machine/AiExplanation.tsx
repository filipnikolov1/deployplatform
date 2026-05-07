"use client";

/**
 * AiExplanation — restyled AI narration card with violet kicker + sparkle icon.
 * Inline [N] citations are clickable chips that jump to receipts.
 * Drop-in replacement for AIAnalysisPanel.
 */

import { ReactNode, useMemo } from "react";
import { Loader2, RefreshCw, Sparkles } from "lucide-react";
import ReactMarkdown from "react-markdown";
import { motion } from "framer-motion";
import { M } from "@/design/tokens";
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

function renderTextWithCitations(text: string, onJump: (id: number) => void): ReactNode[] {
  const nodes: ReactNode[] = [];
  let lastIdx = 0;
  let match: RegExpExecArray | null;
  let key = 0;
  CITATION_RX.lastIndex = 0;
  while ((match = CITATION_RX.exec(text)) !== null) {
    if (match.index > lastIdx) nodes.push(text.slice(lastIdx, match.index));
    const id = Number(match[1]);
    nodes.push(
      <button
        key={`cite-${key++}-${match.index}`}
        type="button"
        onClick={() => onJump(id)}
        style={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          verticalAlign: "baseline",
          margin: "0 1px",
          padding: "0 5px",
          height: 18,
          borderRadius: M.rPill,
          fontSize: 10,
          fontWeight: 600,
          background: M.accentSoft,
          color: M.accentLight,
          border: `1px solid ${M.accentLine}`,
          cursor: "pointer",
          fontFamily: M.fontMono,
        }}
        aria-label={`Jump to receipt ${id}`}
      >
        {id}
      </button>
    );
    lastIdx = match.index + match[0].length;
  }
  if (lastIdx < text.length) nodes.push(text.slice(lastIdx));
  return nodes;
}

function processChildren(children: ReactNode, onJump: (id: number) => void): ReactNode {
  if (typeof children === "string") return renderTextWithCitations(children, onJump);
  if (Array.isArray(children)) {
    return children.map((c, i) =>
      typeof c === "string" ? (
        <span key={`s-${i}`}>{renderTextWithCitations(c, onJump)}</span>
      ) : (
        c
      )
    );
  }
  return children;
}

export function AiExplanation({
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

  const components = useMemo(
    () => ({
      p: ({ children }: { children?: ReactNode }) => (
        <p
          style={{
            marginBottom: 12,
            fontSize: 14,
            color: M.fg,
            lineHeight: 1.65,
            letterSpacing: "-0.005em",
          }}
        >
          {processChildren(children, onJumpToReceipt)}
        </p>
      ),
      strong: ({ children }: { children?: ReactNode }) => (
        <strong style={{ color: M.fg }}>
          {processChildren(children, onJumpToReceipt)}
        </strong>
      ),
      em: ({ children }: { children?: ReactNode }) => (
        <em style={{ color: M.fg2 }}>{processChildren(children, onJumpToReceipt)}</em>
      ),
      code: ({ children }: { children?: ReactNode }) => (
        <code
          style={{
            fontFamily: M.fontMono,
            fontSize: 13,
            padding: "1px 5px",
            borderRadius: M.rSm,
            background: M.surface2,
            color: M.fg,
          }}
        >
          {children}
        </code>
      ),
      li: ({ children }: { children?: ReactNode }) => (
        <li
          style={{
            marginLeft: 20,
            listStyleType: "disc",
            fontSize: 14,
            color: M.fg,
            lineHeight: 1.65,
          }}
        >
          {processChildren(children, onJumpToReceipt)}
        </li>
      ),
    }),
    [onJumpToReceipt]
  );

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: 0.1 }}
      style={{
        padding: 24,
        background: M.surface,
        border: `1px solid ${M.accentLine}`,
        borderRadius: M.rLg,
      }}
    >
      {/* Header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 16,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Sparkles size={14} color={M.accent} />
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: M.accentLight,
              letterSpacing: "0.16em",
              textTransform: "uppercase",
              fontFamily: M.fontSans,
            }}
          >
            AI EXPLANATION
          </span>
          {providerUsed && status === "AVAILABLE" && (
            <span
              style={{
                fontSize: 10,
                color: M.fg3,
                textTransform: "uppercase",
                letterSpacing: "0.08em",
              }}
            >
              · {providerUsed}
            </span>
          )}
        </div>

        {canRegenerate && (
          <button
            type="button"
            onClick={onRegenerate}
            disabled={isRegenerating}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 4,
              fontSize: 11,
              padding: "4px 10px",
              borderRadius: M.rMd,
              background: M.surface2,
              color: M.fg2,
              border: `1px solid ${M.line2}`,
              cursor: isRegenerating ? "not-allowed" : "pointer",
              opacity: isRegenerating ? 0.5 : 1,
            }}
          >
            {isRegenerating ? (
              <Loader2 size={12} style={{ animation: "spin 1s linear infinite" }} />
            ) : (
              <RefreshCw size={12} />
            )}
            Regenerate
          </button>
        )}
      </div>

      {/* Content */}
      {status === "PENDING" && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            fontSize: 12,
            color: M.fg3,
            padding: "8px 0",
          }}
        >
          <Loader2 size={14} style={{ animation: "spin 1s linear infinite" }} />
          Generating AI narration...
        </div>
      )}

      {status === "UNAVAILABLE" && (
        <div style={{ fontSize: 13, color: M.fg3 }}>
          <p style={{ marginBottom: 8 }}>
            AI narration unavailable. Deterministic crash data and receipts are still
            available.
          </p>
          {failureReason && (
            <p style={{ fontFamily: M.fontMono, fontSize: 11, color: M.fg3 }}>
              {failureReason}
            </p>
          )}
          {!canRegenerate && regenerateCount >= regenerateLimit && (
            <p style={{ marginTop: 8, color: M.fg3 }}>
              Regenerate limit reached for this crash.
            </p>
          )}
        </div>
      )}

      {status === "AVAILABLE" && narration && (
        <ReactMarkdown components={components}>{narration}</ReactMarkdown>
      )}
    </motion.div>
  );
}
