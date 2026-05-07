"use client";

/**
 * StatusChip — pill badge with a pulsing dot.
 * The pulse (scale 1 → 2.2 → 1, 2.4s ease-out loop) is the ONLY continuous
 * animation in the design system. Only active for RUNNING and BUILDING states.
 */

import { motion } from "framer-motion";
import { M, MSTATUS, type MStatus } from "@/design/tokens";

interface StatusChipProps {
  status: MStatus;
  /** Override the default label from MSTATUS[status].label */
  label?: string;
  compact?: boolean;
}

export function StatusChip({ status, label, compact = false }: StatusChipProps) {
  const s = MSTATUS[status] ?? MSTATUS.STOPPED;
  const shouldPulse = status === "RUNNING" || status === "BUILDING";

  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: compact ? "2px 8px" : "4px 10px",
        borderRadius: M.rPill,
        fontSize: compact ? 11 : 12,
        fontWeight: 500,
        fontFamily: M.fontSans,
        background: s.bg,
        border: `1px solid ${s.line}`,
        color: s.text,
        letterSpacing: "-0.005em",
      }}
    >
      {/* Dot wrapper — relative so the pulse aura can be absolutely positioned */}
      <span
        style={{
          position: "relative",
          display: "inline-flex",
          width: 6,
          height: 6,
          flexShrink: 0,
        }}
      >
        {shouldPulse && (
          <motion.span
            animate={{ opacity: [0.6, 0, 0.6], scale: [1, 2.2, 1] }}
            transition={{ duration: 2.4, repeat: Infinity, ease: "easeOut" }}
            style={{
              position: "absolute",
              inset: 0,
              borderRadius: "50%",
              background: s.dot,
            }}
          />
        )}
        {/* Solid dot always rendered on top */}
        <span
          style={{
            position: "relative",
            width: 6,
            height: 6,
            borderRadius: "50%",
            background: s.dot,
          }}
        />
      </span>
      {label ?? s.label}
    </span>
  );
}
