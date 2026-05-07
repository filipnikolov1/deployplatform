"use client";

/**
 * Stepper — numbered-circle stepper with animated connecting lines.
 * Connecting lines fill via motion.div scaleX 0→1 transformOrigin="left", 400ms cubic.
 * Terminal node morphs to check/x via spring scale + crossfade when terminalState set.
 */

import { AnimatePresence, motion } from "framer-motion";
import { M } from "@/design/tokens";
import { Icon } from "./primitives/Icon";

export type StepperTerminalState = "success" | "failure" | null;

export interface StepItem {
  id: string;
  label: string;
}

interface StepperProps {
  steps: StepItem[];
  currentIndex: number;
  terminalState?: StepperTerminalState;
  size?: "mini" | "large";
}

const SIZE_CONFIG = {
  mini: {
    nodeSize: 20,
    fontSize: 11,
    labelFontSize: 11,
    lineHeight: 2,
    gap: 8,
    iconSize: 10,
  },
  large: {
    nodeSize: 32,
    fontSize: 13,
    labelFontSize: 13,
    lineHeight: 3,
    gap: 12,
    iconSize: 14,
  },
} as const;

export function Stepper({
  steps,
  currentIndex,
  terminalState = null,
  size = "large",
}: StepperProps) {
  const cfg = SIZE_CONFIG[size];
  const lastIndex = steps.length - 1;

  return (
    <div
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: 0,
      }}
    >
      {steps.map((step, i) => {
        const isDone = i < currentIndex;
        const isActive = i === currentIndex;
        const isLast = i === lastIndex;
        const showTerminal = isLast && isActive && terminalState !== null;

        // Node appearance
        let nodeBackground: string = M.surface2;
        let nodeBorder: string = M.line2;
        let nodeColor: string = M.fg3;

        if (showTerminal) {
          if (terminalState === "success") {
            nodeBackground = M.okSoft;
            nodeBorder = "rgba(134,239,172,0.22)";
            nodeColor = M.ok;
          } else {
            nodeBackground = M.errSoft;
            nodeBorder = "rgba(252,165,165,0.22)";
            nodeColor = M.err;
          }
        } else if (isDone) {
          nodeBackground = M.accentSoft;
          nodeBorder = M.accentLine;
          nodeColor = M.accentLight;
        } else if (isActive) {
          nodeBackground = M.surface3;
          nodeBorder = M.line3;
          nodeColor = M.fg;
        }

        return (
          <div
            key={step.id}
            style={{
              display: "flex",
              alignItems: "center",
              flex: isLast ? "0 0 auto" : "1 1 auto",
              minWidth: 0,
            }}
          >
            {/* Node + label column */}
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: cfg.gap / 2,
                flexShrink: 0,
              }}
            >
              {/* Circle node */}
              <motion.div
                animate={{
                  background: nodeBackground,
                  borderColor: nodeBorder,
                }}
                transition={{ type: "spring", stiffness: 320, damping: 28 }}
                style={{
                  width: cfg.nodeSize,
                  height: cfg.nodeSize,
                  borderRadius: "50%",
                  border: `1px solid ${nodeBorder}`,
                  background: nodeBackground,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  position: "relative",
                  overflow: "hidden",
                  flexShrink: 0,
                }}
              >
                <AnimatePresence mode="wait" initial={false}>
                  {showTerminal ? (
                    <motion.span
                      key={`terminal-${terminalState}`}
                      initial={{ scale: 0, opacity: 0 }}
                      animate={{ scale: 1, opacity: 1 }}
                      exit={{ scale: 0, opacity: 0 }}
                      transition={{ type: "spring", stiffness: 400, damping: 26 }}
                      style={{ display: "flex", alignItems: "center", justifyContent: "center" }}
                    >
                      <Icon
                        name={terminalState === "success" ? "check" : "x"}
                        size={cfg.iconSize}
                        color={nodeColor}
                        strokeWidth={2}
                      />
                    </motion.span>
                  ) : isDone ? (
                    <motion.span
                      key="done"
                      initial={{ scale: 0, opacity: 0 }}
                      animate={{ scale: 1, opacity: 1 }}
                      exit={{ scale: 0, opacity: 0 }}
                      transition={{ type: "spring", stiffness: 400, damping: 26 }}
                      style={{ display: "flex", alignItems: "center", justifyContent: "center" }}
                    >
                      <Icon name="check" size={cfg.iconSize} color={nodeColor} strokeWidth={2} />
                    </motion.span>
                  ) : (
                    <motion.span
                      key="number"
                      initial={{ scale: 0, opacity: 0 }}
                      animate={{ scale: 1, opacity: 1 }}
                      exit={{ scale: 0, opacity: 0 }}
                      transition={{ type: "spring", stiffness: 400, damping: 26 }}
                      style={{
                        fontSize: cfg.fontSize,
                        fontFamily: M.fontMono,
                        fontWeight: 500,
                        color: nodeColor,
                        lineHeight: 1,
                        userSelect: "none",
                      }}
                    >
                      {i + 1}
                    </motion.span>
                  )}
                </AnimatePresence>

                {/* Active pulse ring */}
                {isActive && !showTerminal && (
                  <motion.span
                    animate={{ opacity: [0.4, 0, 0.4], scale: [1, 1.6, 1] }}
                    transition={{ duration: 2.4, repeat: Infinity, ease: "easeOut" }}
                    style={{
                      position: "absolute",
                      inset: -2,
                      borderRadius: "50%",
                      border: `1px solid ${M.line2}`,
                    }}
                  />
                )}
              </motion.div>

              {/* Step label */}
              <span
                style={{
                  fontSize: cfg.labelFontSize,
                  fontFamily: M.fontSans,
                  fontWeight: 500,
                  color: isActive || isDone ? M.fg : M.fg3,
                  whiteSpace: "nowrap",
                  transition: "color 200ms",
                }}
              >
                {step.label}
              </span>
            </div>

            {/* Connecting line (not after last node) */}
            {!isLast && (
              <div
                style={{
                  flex: 1,
                  height: cfg.lineHeight,
                  position: "relative",
                  overflow: "hidden",
                  background: M.line,
                  borderRadius: 999,
                  margin: `0 ${cfg.gap / 2}px`,
                  // Align with center of node (offset half the label font size + gap)
                  marginBottom: cfg.labelFontSize + cfg.gap / 2,
                }}
              >
                <motion.div
                  initial={{ scaleX: 0 }}
                  animate={{ scaleX: isDone ? 1 : 0 }}
                  transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
                  style={{
                    position: "absolute",
                    inset: 0,
                    background: M.accentSoft,
                    transformOrigin: "left",
                    borderRadius: 999,
                  }}
                />
                {/* Violet fill for completed segments */}
                <motion.div
                  initial={{ scaleX: 0 }}
                  animate={{ scaleX: isDone ? 1 : 0 }}
                  transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1], delay: 0.05 }}
                  style={{
                    position: "absolute",
                    inset: 0,
                    background: M.accent,
                    opacity: 0.5,
                    transformOrigin: "left",
                    borderRadius: 999,
                  }}
                />
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
