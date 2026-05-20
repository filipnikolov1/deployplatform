"use client";

/**
 * OperationProgress — shared progress bar component.
 * Subscribes to useOperationProgress(operationId).
 * Auto-disappears 300ms after the stream closes (percent reaches 100 or goes null).
 *
 * Variants:
 *   inline — 4px tall, no padding, sits flush under content
 *   banner — 8px tall + stage label inline above the bar
 *   toast  — 4px tall, embedded in a sonner toast row
 */

import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import { useOperationProgress } from "@/hooks/useOperationProgress";

export type OperationProgressVariant = "inline" | "banner" | "toast";

interface OperationProgressProps {
  operationId: string;
  variant?: OperationProgressVariant;
}

const STAGE_LABEL_MAP: Record<string, string> = {
  PULL_LAYER: "Pulling",
  CONTAINER_CREATE: "Creating container",
  CONTAINER_START: "Starting",
  HEALTH_PROBE: "Health check",
  UPDATER_RECREATE: "Restarting updater",
  UPDATER_BOOT: "Booting updater",
};

export function OperationProgress({
  operationId,
  variant = "inline",
}: OperationProgressProps) {
  const { stage, message, percent, streamClosed } = useOperationProgress(operationId);
  const [visible, setVisible] = useState(true);
  const hideTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (streamClosed) {
      hideTimerRef.current = setTimeout(() => setVisible(false), 300);
    } else {
      if (hideTimerRef.current) {
        clearTimeout(hideTimerRef.current);
        hideTimerRef.current = null;
      }
      setVisible(true);
    }
    return () => {
      if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    };
  }, [streamClosed]);

  // Build stage label for banner variant
  const stageLabel = (() => {
    if (message) return message;
    if (!stage) return null;
    const base = STAGE_LABEL_MAP[stage] ?? stage;
    if (percent !== null) return `${base} · ${percent}%`;
    return base;
  })();

  const barHeight = variant === "banner" ? 8 : 4;

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          style={{
            width: "100%",
            overflow: "hidden",
          }}
        >
          {/* Stage label — banner variant only */}
          {variant === "banner" && stageLabel && (
            <div
              style={{
                fontSize: 11,
                fontFamily: M.fontMono,
                color: M.fg3,
                letterSpacing: "0.04em",
                marginBottom: 4,
              }}
            >
              {stageLabel}
            </div>
          )}

          {/* Progress track */}
          <div
            style={{
              width: "100%",
              height: barHeight,
              background: M.surface2,
              borderRadius: M.rPill,
              overflow: "hidden",
              position: "relative",
            }}
          >
            {percent !== null ? (
              /* Determinate bar */
              <motion.div
                initial={{ width: 0 }}
                animate={{ width: `${percent}%` }}
                transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
                style={{
                  height: "100%",
                  background: M.accent,
                  opacity: 0.85,
                  borderRadius: M.rPill,
                }}
              />
            ) : (
              /* Indeterminate shimmer */
              <IndeterminateBar />
            )}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/** Continuous indeterminate shimmer — slides a short bar back and forth. */
function IndeterminateBar() {
  return (
    <motion.div
      animate={{ x: ["0%", "300%"] }}
      transition={{
        duration: 1.4,
        ease: "easeInOut",
        repeat: Infinity,
        repeatType: "mirror",
      }}
      style={{
        position: "absolute",
        left: 0,
        top: 0,
        height: "100%",
        width: "33%",
        background: `linear-gradient(90deg, transparent, ${M.accent}, transparent)`,
        opacity: 0.7,
        borderRadius: "inherit",
      }}
    />
  );
}
