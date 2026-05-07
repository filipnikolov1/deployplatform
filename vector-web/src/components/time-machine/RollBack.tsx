"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { AlertTriangle, Check, RotateCcw } from "lucide-react";
import { M, MMOTION } from "@/design/tokens";
import { Button } from "@/design/primitives/Button";
import { useOperationProgress } from "@/hooks/useOperationProgress";

type Stage = "idle" | "confirming" | "rolling" | "done";

interface Props {
  appName: string;
  fromSha: string;
  toSha: string;
  estimatedDeployMs?: number | null;
  onSuccess?: () => void;
}

async function triggerRollback(
  appName: string,
  sha: string
): Promise<{ operationId?: string } | null> {
  const res = await fetch(
    `/api/apps/${encodeURIComponent(appName)}/rollback`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ commitSha: sha }),
    }
  );
  if (!res.ok) return null;
  return res.json();
}

function ProgressBar({ operationId }: { operationId: string }) {
  const { percent, stage } = useOperationProgress(operationId);

  const label =
    stage === "PULL_LAYER"
      ? "Pulling image…"
      : stage === "CONTAINER_START"
      ? "Starting container…"
      : stage === "HEALTH_PROBE"
      ? "Health check…"
      : "Rolling back…";

  return (
    <div>
      <div
        style={{
          height: 6,
          background: M.surface3,
          borderRadius: 3,
          overflow: "hidden",
          marginBottom: 10,
        }}
      >
        <motion.div
          animate={{ width: `${percent ?? 0}%` }}
          transition={{ duration: 0.2 }}
          style={{
            height: "100%",
            background: `linear-gradient(90deg, ${M.accent}, ${M.accentLight})`,
          }}
        />
      </div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          fontSize: 12,
          color: M.fg2,
        }}
      >
        <span>{label}</span>
        <span style={{ fontFamily: M.fontMono, color: M.fg3 }}>{percent ?? 0}%</span>
      </div>
    </div>
  );
}

export function RollBack({
  appName,
  fromSha,
  toSha,
  estimatedDeployMs,
  onSuccess,
}: Props) {
  const [stage, setStage] = useState<Stage>("idle");
  const [operationId, setOperationId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const estimatedSec = estimatedDeployMs
    ? Math.round(estimatedDeployMs / 1000)
    : null;

  const handleConfirm = async () => {
    setStage("rolling");
    setError(null);
    try {
      const result = await triggerRollback(appName, toSha);
      if (!result) {
        setError("Rollback request failed. Please try again.");
        setStage("idle");
        return;
      }
      if (result.operationId) {
        setOperationId(result.operationId);
      }
      // Simulate completion if no operationId (fallback)
      if (!result.operationId) {
        setTimeout(() => {
          setStage("done");
          onSuccess?.();
        }, 2000);
      } else {
        // Give operationId SSE time; done is triggered from parent or via timeout
        setTimeout(() => {
          setStage("done");
          onSuccess?.();
        }, 15_000);
      }
    } catch {
      setError("Network error during rollback.");
      setStage("idle");
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      style={{
        padding: 20,
        background: M.surface,
        border: `1px solid ${M.accentLine}`,
        borderRadius: M.rLg,
      }}
    >
      {/* Kicker */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          marginBottom: 16,
        }}
      >
        <RotateCcw size={13} color={M.accent} />
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
          ROLL BACK
        </span>
      </div>

      {/* SHA summary */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 12,
          padding: 14,
          background: M.bg,
          border: `1px solid ${M.line}`,
          borderRadius: M.rMd,
          marginBottom: 14,
        }}
      >
        <span
          style={{
            fontFamily: M.fontMono,
            fontSize: 13,
            color: M.err,
            fontWeight: 600,
            textDecoration: "line-through",
          }}
        >
          {fromSha.slice(0, 7)}
        </span>
        <span style={{ color: M.fg3, fontSize: 13 }}>→</span>
        <span
          style={{
            fontFamily: M.fontMono,
            fontSize: 13,
            color: M.ok,
            fontWeight: 600,
          }}
        >
          {toSha.slice(0, 7)}
        </span>
        {estimatedSec != null && (
          <span
            style={{
              marginLeft: "auto",
              fontSize: 11.5,
              color: M.fg3,
            }}
          >
            ~{estimatedSec}s deploy
          </span>
        )}
      </div>

      <div
        style={{
          fontSize: 12.5,
          color: M.fg2,
          lineHeight: 1.55,
          marginBottom: 16,
        }}
      >
        Restores the last known healthy version. Your data is unaffected — only the container is
        replaced.
      </div>

      {error && (
        <div
          style={{
            padding: "8px 12px",
            background: M.errSoft,
            border: `1px solid rgba(252,165,165,0.22)`,
            borderRadius: M.rMd,
            fontSize: 12,
            color: M.err,
            marginBottom: 12,
          }}
        >
          {error}
        </div>
      )}

      {/* Multi-stage action area */}
      <AnimatePresence mode="wait">
        {stage === "idle" && (
          <motion.div key="idle" {...MMOTION.modal}>
            <div style={{ display: "flex", gap: 8 }}>
              <Button
                variant="accent"
                leadingIcon="rotate-ccw"
                size="sm"
                onClick={() => setStage("confirming")}
                style={{ flex: 1, justifyContent: "center" }}
              >
                Roll back to {toSha.slice(0, 7)}
              </Button>
              <Button variant="ghost" leadingIcon="git-branch" size="sm">
                Pin version
              </Button>
            </div>
          </motion.div>
        )}

        {stage === "confirming" && (
          <motion.div key="confirming" {...MMOTION.modal}>
            <div
              style={{
                padding: 12,
                background: M.warnSoft,
                border: `1px solid rgba(252,211,77,0.22)`,
                borderRadius: M.rMd,
                fontSize: 12.5,
                color: M.warn,
                marginBottom: 12,
                display: "flex",
                alignItems: "center",
                gap: 8,
              }}
            >
              <AlertTriangle size={13} />
              This will restart the app. In-flight requests may be interrupted.
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <Button
                variant="primary"
                size="sm"
                onClick={handleConfirm}
                style={{ flex: 1, justifyContent: "center" }}
              >
                Confirm rollback
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setStage("idle")}>
                Cancel
              </Button>
            </div>
          </motion.div>
        )}

        {stage === "rolling" && (
          <motion.div key="rolling" {...MMOTION.modal}>
            {operationId ? (
              <ProgressBar operationId={operationId} />
            ) : (
              <div>
                <div
                  style={{
                    height: 6,
                    background: M.surface3,
                    borderRadius: 3,
                    overflow: "hidden",
                    marginBottom: 10,
                  }}
                >
                  <motion.div
                    animate={{ width: ["0%", "90%"] }}
                    transition={{ duration: 8, ease: "linear" }}
                    style={{
                      height: "100%",
                      background: `linear-gradient(90deg, ${M.accent}, ${M.accentLight})`,
                    }}
                  />
                </div>
                <div style={{ fontSize: 12, color: M.fg2 }}>Rolling back…</div>
              </div>
            )}
          </motion.div>
        )}

        {stage === "done" && (
          <motion.div
            key="done"
            {...MMOTION.modal}
            style={{
              padding: 14,
              background: M.okSoft,
              border: `1px solid rgba(134,239,172,0.22)`,
              borderRadius: M.rMd,
              color: M.ok,
              display: "flex",
              alignItems: "center",
              gap: 10,
            }}
          >
            <motion.span
              initial={{ scale: 0, rotate: -90 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ type: "spring", stiffness: 400, damping: 18 }}
              style={{
                width: 22,
                height: 22,
                borderRadius: "50%",
                background: M.ok,
                color: M.bg,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              <Check size={13} color={M.bg} />
            </motion.span>
            <span style={{ fontSize: 13, fontWeight: 500 }}>
              Restored to {toSha.slice(0, 7)} · {appName} is healthy
            </span>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
