"use client";

import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { M, MMOTION } from "@/design/tokens";
import { useSetupStatus } from "@/hooks/useSetupStatus";

interface Check {
  key: "dockerReachable" | "secretConfigured" | "githubTokenConfigured" | "outbound";
  label: string;
  subline: (ok: boolean) => string;
}

const CHECKS: Check[] = [
  {
    key: "dockerReachable",
    label: "Docker socket",
    subline: (ok) => (ok ? "daemon reachable · images accessible" : "cannot reach Docker daemon"),
  },
  {
    key: "secretConfigured",
    label: "Deploy hook secret",
    subline: (ok) => (ok ? "configured" : "VECTOR_SECRET not set"),
  },
  {
    key: "githubTokenConfigured",
    label: "GitHub token",
    subline: (ok) => (ok ? "configured" : "GITHUB_TOKEN not set"),
  },
  {
    key: "outbound",
    label: "Outbound to GitHub",
    subline: (ok) => (ok ? "api.github.com reachable" : "network check unavailable"),
  },
];

interface StatusDotProps {
  ok: boolean;
  pulse: boolean;
}

function StatusDot({ ok, pulse }: StatusDotProps) {
  const color = ok ? M.ok : M.err;
  const bg = ok ? M.okSoft : M.errSoft;

  return (
    <span
      style={{
        position: "relative",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        width: 20,
        height: 20,
        borderRadius: "50%",
        background: bg,
        flexShrink: 0,
        marginTop: 1,
      }}
    >
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: "50%",
          background: color,
          display: "block",
        }}
      />
      {pulse && (
        <motion.span
          key={`pulse-${ok}`}
          animate={{ opacity: [0.6, 0, 0.6], scale: [1, 1.9, 1] }}
          transition={{ duration: 1.4, repeat: 3, ease: "easeOut" }}
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: "50%",
            border: `1.5px solid ${color}`,
            opacity: 0.6,
          }}
        />
      )}
    </span>
  );
}

export function EnvironmentPanel() {
  const { status } = useSetupStatus();

  // Track previous status values to detect flips for pulse animation
  const prevStatus = useRef<typeof status>(null);
  const [pulsing, setPulsing] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!status || !prevStatus.current) {
      prevStatus.current = status;
      return;
    }
    const flipped: Record<string, boolean> = {};
    (["dockerReachable", "secretConfigured", "githubTokenConfigured"] as const).forEach((key) => {
      if (status[key] !== prevStatus.current![key]) {
        flipped[key] = true;
      }
    });
    if (Object.keys(flipped).length > 0) {
      setPulsing(flipped);
      setTimeout(() => setPulsing({}), 2000);
    }
    prevStatus.current = status;
  }, [status]);

  // "outbound" is derived: we can't ping from the browser, so we treat it as true if
  // the backend itself is reachable (i.e., we got a response from /api/setup/status)
  const resolved = status
    ? {
        dockerReachable: status.dockerReachable,
        secretConfigured: status.secretConfigured,
        githubTokenConfigured: status.githubTokenConfigured,
        outbound: true,
      }
    : null;

  return (
    <aside
      style={{
        position: "sticky",
        top: 32,
      }}
    >
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: "0.16em",
          textTransform: "uppercase",
          color: M.fg3,
          marginBottom: 16,
          fontFamily: M.fontSans,
        }}
      >
        Environment
      </div>

      <div
        style={{
          background: M.surface,
          border: `1px solid ${M.line}`,
          borderRadius: M.rLg,
          overflow: "hidden",
        }}
      >
        <motion.div
          variants={MMOTION.list}
          initial="initial"
          animate="animate"
          style={{ display: "flex", flexDirection: "column" }}
        >
          {CHECKS.map((check, i) => {
            const ok = resolved ? resolved[check.key] : false;
            const isLast = i === CHECKS.length - 1;

            return (
              <motion.div
                key={check.key}
                variants={MMOTION.item}
                style={{
                  display: "flex",
                  alignItems: "flex-start",
                  gap: 12,
                  padding: "14px 16px",
                  borderBottom: isLast ? "none" : `1px solid ${M.line}`,
                }}
              >
                <AnimatePresence mode="wait">
                  <motion.span
                    key={`dot-${check.key}-${ok}`}
                    initial={{ scale: 0.8, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    exit={{ scale: 0.8, opacity: 0 }}
                    transition={{ duration: 0.2 }}
                  >
                    <StatusDot ok={resolved ? ok : false} pulse={!!pulsing[check.key]} />
                  </motion.span>
                </AnimatePresence>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      fontSize: 12.5,
                      fontWeight: 500,
                      color: resolved ? M.fg : M.fg3,
                      fontFamily: M.fontSans,
                      letterSpacing: "-0.005em",
                    }}
                  >
                    {check.label}
                  </div>
                  <div
                    style={{
                      fontSize: 11.5,
                      color: M.fg3,
                      marginTop: 2,
                      fontFamily: M.fontSans,
                    }}
                  >
                    {resolved ? check.subline(ok) : "checking…"}
                  </div>
                </div>
              </motion.div>
            );
          })}
        </motion.div>
      </div>
    </aside>
  );
}
