"use client";

import { useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import { formatDistanceToNow } from "date-fns";

export interface TimelineNode {
  id: string | number;
  /** Fractional position in [0,1] across the 14-day rail, 0 = -14d, 1 = now */
  t: number;
  kind: "deploy" | "commit" | "restart" | "crash";
  sha?: string;
  msg?: string;
  occurredAt?: Date;
}

const KIND_COLOR: Record<TimelineNode["kind"], string> = {
  deploy: M.ok,
  commit: M.fg2,
  restart: M.warn,
  crash: M.err,
};

const KIND_LABEL: Record<TimelineNode["kind"], string> = {
  deploy: "Deploy",
  commit: "Commit",
  restart: "Restart",
  crash: "Crash",
};

const TICK_LABELS = ["-2w", "-1w", "-3d", "-1d", "now"];

interface Props {
  nodes: TimelineNode[];
  scrub: number; // 0..1
  onScrub: (v: number) => void;
  onNodeClick?: (node: TimelineNode) => void;
}

interface Popup {
  node: TimelineNode;
  leftPct: number;
}

export function HorizontalTimeline({ nodes, scrub, onScrub, onNodeClick }: Props) {
  const railRef = useRef<HTMLDivElement>(null);
  const [popup, setPopup] = useState<Popup | null>(null);

  const handleRailClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rail = railRef.current;
    if (!rail) return;
    const rect = rail.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    onScrub(Math.max(0, Math.min(1, x)));
    setPopup(null);
  };

  return (
    <div
      style={{
        position: "relative",
        height: 88,
        padding: "0 14px",
        background: M.surface,
        border: `1px solid ${M.line}`,
        borderRadius: M.rLg,
        userSelect: "none",
      }}
    >
      {/* Tick labels */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          fontSize: 10.5,
          color: M.fg3,
          fontFamily: M.fontMono,
          padding: "10px 4px 0",
        }}
      >
        {TICK_LABELS.map((l) => (
          <span key={l}>{l}</span>
        ))}
      </div>

      {/* Rail */}
      <div
        ref={railRef}
        onClick={handleRailClick}
        style={{
          position: "absolute",
          left: 14,
          right: 14,
          top: "50%",
          height: 2,
          background: M.line2,
          borderRadius: 1,
          cursor: "pointer",
          transform: "translateY(6px)",
        }}
      >
        {/* Active fill */}
        <motion.div
          animate={{ width: `${scrub * 100}%` }}
          transition={{ type: "spring", stiffness: 300, damping: 30 }}
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            bottom: 0,
            background: `linear-gradient(90deg, ${M.accent}, ${M.accentLight})`,
            borderRadius: 1,
          }}
        />

        {/* Nodes */}
        {nodes.map((node) => {
          const leftPct = node.t * 100;
          const reached = node.t <= scrub + 0.005;
          const isCrash = node.kind === "crash";
          const dotColor = reached ? KIND_COLOR[node.kind] : M.surface3;
          const dotSize = isCrash ? 14 : 10;

          return (
            <motion.button
              key={node.id}
              type="button"
              whileHover={{ scale: 1.4 }}
              transition={{ type: "spring", stiffness: 400, damping: 22 }}
              onClick={(e) => {
                e.stopPropagation();
                onScrub(node.t);
                setPopup((prev) =>
                  prev?.node.id === node.id ? null : { node, leftPct }
                );
                onNodeClick?.(node);
              }}
              style={{
                position: "absolute",
                left: `${leftPct}%`,
                top: "50%",
                width: dotSize,
                height: dotSize,
                borderRadius: "50%",
                background: dotColor,
                border: `2px solid ${M.bg}`,
                transform: "translate(-50%, -50%)",
                cursor: "pointer",
                padding: 0,
                boxShadow: isCrash && reached ? `0 0 18px ${M.err}66` : "none",
                zIndex: 1,
              }}
            />
          );
        })}

        {/* Crash pulse aura */}
        {nodes
          .filter((n) => n.kind === "crash" && n.t <= scrub + 0.005)
          .map((n) => (
            <motion.div
              key={`aura-${n.id}`}
              animate={{ scale: [1, 2.2, 1], opacity: [0.6, 0, 0.6] }}
              transition={{ duration: 2.2, repeat: Infinity }}
              style={{
                position: "absolute",
                left: `${n.t * 100}%`,
                top: "50%",
                width: 14,
                height: 14,
                borderRadius: "50%",
                background: M.err,
                transform: "translate(-50%, -50%)",
                pointerEvents: "none",
              }}
            />
          ))}

        {/* Scrub handle */}
        <motion.div
          animate={{ left: `${scrub * 100}%` }}
          transition={{ type: "spring", stiffness: 300, damping: 30 }}
          style={{
            position: "absolute",
            top: "50%",
            transform: "translate(-50%, -50%)",
            width: 16,
            height: 16,
            borderRadius: "50%",
            background: M.fg,
            border: `3px solid ${M.bg}`,
            boxShadow: `0 0 0 1px ${M.line3}, 0 4px 12px rgba(0,0,0,0.5)`,
            zIndex: 2,
            pointerEvents: "none",
          }}
        />
      </div>

      {/* Popup tooltip */}
      <AnimatePresence>
        {popup && (
          <motion.div
            key={`popup-${popup.node.id}`}
            initial={{ opacity: 0, y: 8, scale: 0.92 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 4, scale: 0.96 }}
            transition={{ type: "spring", stiffness: 380, damping: 28 }}
            style={{
              position: "absolute",
              left: `calc(${popup.leftPct}% + 14px)`,
              top: 8,
              transform: "translateX(-50%)",
              background: M.surface2,
              border: `1px solid ${M.line2}`,
              borderRadius: M.rMd,
              padding: "10px 14px",
              minWidth: 160,
              zIndex: 10,
              pointerEvents: "none",
            }}
          >
            <div
              style={{
                fontSize: 10,
                fontWeight: 700,
                letterSpacing: "0.12em",
                textTransform: "uppercase",
                color: KIND_COLOR[popup.node.kind],
                marginBottom: 4,
              }}
            >
              {KIND_LABEL[popup.node.kind]}
            </div>
            {popup.node.sha && (
              <div
                style={{
                  fontFamily: M.fontMono,
                  fontSize: 12,
                  color: M.fg,
                  marginBottom: 2,
                }}
              >
                {popup.node.sha.slice(0, 7)}
              </div>
            )}
            {popup.node.occurredAt && (
              <div style={{ fontSize: 11, color: M.fg3 }}>
                {formatDistanceToNow(popup.node.occurredAt, { addSuffix: true })}
              </div>
            )}
            {popup.node.msg && (
              <div
                style={{
                  fontSize: 11,
                  color: M.fg2,
                  marginTop: 4,
                  maxWidth: 200,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {popup.node.msg}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
