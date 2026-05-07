"use client";

import { motion, AnimatePresence } from "framer-motion";
import { M, MMOTION } from "@/design/tokens";
import { formatDistanceToNow } from "date-fns";
import type { RecentDeploy } from "@/types/analyzer";

interface Props {
  deploys: RecentDeploy[];
  currentSha: string | null;
  selectedSha: string | null;
  onSelect: (sha: string) => void;
}

function isRollback(metadata: string): boolean {
  try {
    const m = JSON.parse(metadata);
    return m.triggerSource === "ROLLBACK" || m.eventType === "MANUAL_ROLLBACK";
  } catch {
    return false;
  }
}

export function RecentDeploys({ deploys, currentSha, selectedSha, onSelect }: Props) {
  if (deploys.length === 0) {
    return (
      <div
        style={{
          padding: "24px 16px",
          textAlign: "center",
          color: M.fg3,
          fontSize: 13,
        }}
      >
        No deploys yet. The first successful deploy will appear here.
      </div>
    );
  }

  return (
    <motion.div
      variants={MMOTION.list}
      initial="initial"
      animate="animate"
      style={{
        border: `1px solid ${M.line}`,
        borderRadius: M.rLg,
        background: M.surface,
        overflow: "hidden",
      }}
    >
      <AnimatePresence initial={false}>
        {deploys.map((deploy, i) => {
          const isCurrent = deploy.commitSha === currentSha;
          const isSelected = deploy.commitSha === selectedSha;
          const rollback = isRollback(deploy.metadata);
          const date = new Date(deploy.occurredAt);

          return (
            <motion.div
              key={deploy.id}
              variants={MMOTION.item}
              layout
              onClick={() => onSelect(deploy.commitSha)}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 14,
                padding: "14px 18px",
                cursor: "pointer",
                borderBottom:
                  i < deploys.length - 1 ? `1px solid ${M.line}` : "none",
                background: isSelected ? M.accentSoft : "transparent",
                transition: "background 120ms",
              }}
              onMouseEnter={(e) => {
                if (!isSelected)
                  (e.currentTarget as HTMLElement).style.background = M.surface2;
              }}
              onMouseLeave={(e) => {
                if (!isSelected)
                  (e.currentTarget as HTMLElement).style.background = "transparent";
              }}
            >
              {/* Status dot */}
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: "50%",
                  background: isCurrent ? M.ok : M.fg3,
                  flexShrink: 0,
                }}
              />

              {/* SHA */}
              <span
                style={{
                  fontFamily: M.fontMono,
                  fontSize: 12,
                  fontWeight: 500,
                  color: isSelected ? M.accentLight : M.fg,
                  flexShrink: 0,
                  minWidth: 60,
                }}
              >
                {deploy.commitSha.slice(0, 7)}
              </span>

              {/* Rollback badge */}
              {rollback && (
                <span
                  style={{
                    padding: "2px 7px",
                    borderRadius: M.rPill,
                    fontSize: 10,
                    fontWeight: 600,
                    letterSpacing: "0.08em",
                    textTransform: "uppercase",
                    background: "transparent",
                    color: M.fg3,
                    border: `1px solid ${M.line2}`,
                    flexShrink: 0,
                  }}
                >
                  rollback
                </span>
              )}

              {/* Current badge */}
              {isCurrent && (
                <span
                  style={{
                    padding: "2px 8px",
                    borderRadius: M.rPill,
                    fontSize: 10,
                    fontWeight: 600,
                    letterSpacing: "0.08em",
                    textTransform: "uppercase",
                    background: M.okSoft,
                    color: M.ok,
                    border: `1px solid rgba(134,239,172,0.22)`,
                    flexShrink: 0,
                  }}
                >
                  running
                </span>
              )}

              {/* Spacer */}
              <span style={{ flex: 1 }} />

              {/* Timestamp */}
              <span
                style={{
                  fontSize: 11.5,
                  color: M.fg3,
                  fontVariantNumeric: "tabular-nums",
                  flexShrink: 0,
                }}
              >
                {formatDistanceToNow(date, { addSuffix: true })}
              </span>

              {/* Type chip */}
              <span
                style={{
                  padding: "2px 8px",
                  borderRadius: M.rPill,
                  fontSize: 10,
                  fontWeight: 600,
                  letterSpacing: "0.10em",
                  textTransform: "uppercase",
                  color: M.ok,
                  background: M.okSoft,
                  border: `1px solid rgba(134,239,172,0.22)`,
                  flexShrink: 0,
                }}
              >
                DEPLOY
              </span>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </motion.div>
  );
}
