"use client";

import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useRouter } from "next/navigation";
import { M } from "@/design/tokens";
import { Button, Icon } from "@/design/primitives";
import { Stepper } from "@/design/Stepper";
import type { StepItem } from "@/design/Stepper";
import { useEventStream } from "@/hooks/useEventStream";
import type { PlatformEventEnvelope } from "@/types/vector";

interface Props {
  appName: string;
  onBack: () => void;
}

const DEPLOY_STEPS: StepItem[] = [
  { id: "triggered", label: "Triggered" },
  { id: "pulling", label: "Pulling" },
  { id: "starting", label: "Starting" },
  { id: "healthy", label: "Healthy" },
];

type DeployState = "waiting" | "received" | "done";

const EVENT_TYPE_TO_STEP: Record<string, number> = {
  DEPLOY_TRIGGERED: 0,
  PULL_STARTED: 1,
  PULL_FINISHED: 2,
  CONTAINER_CREATING: 2,
  CONTAINER_STARTED: 3,
  HEALTH_OK: 3,
  DEPLOY_FINISHED: 3,
};

export function StepDeploy({ appName, onBack }: Props) {
  const router = useRouter();
  const [deployState, setDeployState] = useState<DeployState>("waiting");
  const [stepIndex, setStepIndex] = useState(0);
  const [countdown, setCountdown] = useState(2);
  const [cancelRedirect, setCancelRedirect] = useState(false);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const { lastEventPayload } = useEventStream();

  // Watch SSE for events matching the configured app name
  useEffect(() => {
    if (!lastEventPayload) return;
    try {
      const envelope = JSON.parse(lastEventPayload) as PlatformEventEnvelope;
      if (envelope.version !== 1) return;
      if (envelope.appName !== appName) return;

      if (deployState === "waiting") {
        setDeployState("received");
      }

      const idx = EVENT_TYPE_TO_STEP[envelope.type];
      if (idx !== undefined) {
        setStepIndex((prev) => Math.max(prev, idx));
      }

      // Check if deploy finished
      if (
        envelope.type === "HEALTH_OK" ||
        envelope.type === "DEPLOY_FINISHED" ||
        envelope.status === "SUCCESS"
      ) {
        setStepIndex(DEPLOY_STEPS.length - 1);
        setDeployState("done");
      }
    } catch { /* ignore parse errors */ }
  }, [appName, deployState, lastEventPayload]);

  // Start countdown once first event is received
  useEffect(() => {
    if (deployState !== "received" && deployState !== "done") return;
    if (cancelRedirect) return;

    setCountdown(2);
    const interval = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(interval);
          if (!cancelRedirect) {
            router.push(`/activity?app=${encodeURIComponent(appName)}`);
          }
          return 0;
        }
        return c - 1;
      });
    }, 1000);
    countdownRef.current = interval;
    return () => clearInterval(interval);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deployState]);

  const handleCancel = () => {
    setCancelRedirect(true);
    if (countdownRef.current) clearInterval(countdownRef.current);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 32 }}>
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 24,
          padding: "40px 32px",
          background: M.surface,
          border: `1px solid ${M.line}`,
          borderRadius: M.rLg,
          textAlign: "center",
        }}
      >
        <AnimatePresence mode="wait">
          {deployState === "waiting" ? (
            <motion.div
              key="waiting"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: 16,
              }}
            >
              {/* Pulsing violet dot */}
              <span style={{ position: "relative", display: "inline-flex" }}>
                <span
                  style={{
                    width: 14,
                    height: 14,
                    borderRadius: "50%",
                    background: M.accent,
                    display: "block",
                  }}
                />
                <motion.span
                  animate={{ opacity: [0.5, 0, 0.5], scale: [1, 2.2, 1] }}
                  transition={{ duration: 2, repeat: Infinity, ease: "easeOut" }}
                  style={{
                    position: "absolute",
                    inset: 0,
                    borderRadius: "50%",
                    background: M.accent,
                    opacity: 0.5,
                  }}
                />
              </span>

              <div>
                <div
                  style={{
                    fontSize: 20,
                    fontWeight: 600,
                    color: M.fg,
                    fontFamily: M.fontSans,
                    letterSpacing: "-0.02em",
                    marginBottom: 8,
                  }}
                >
                  Waiting for first deploy
                </div>
                <div
                  style={{
                    fontSize: 14,
                    color: M.fg3,
                    fontFamily: M.fontSans,
                    lineHeight: 1.5,
                    maxWidth: 380,
                  }}
                >
                  Push a commit to{" "}
                  <code
                    style={{
                      fontFamily: M.fontMono,
                      fontSize: 13,
                      color: M.accentLight,
                      background: M.accentSoft,
                      padding: "2px 6px",
                      borderRadius: M.rSm,
                    }}
                  >
                    {appName}
                  </code>{" "}
                  and Vector will pick it up automatically.
                </div>
              </div>

              <Button
                variant="accent"
                size="md"
                trailingIcon="arrow-right"
                onClick={() => router.push("/activity")}
              >
                Open Activity
              </Button>
            </motion.div>
          ) : (
            <motion.div
              key="received"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: 20,
                width: "100%",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  color: M.ok,
                  fontFamily: M.fontSans,
                  fontSize: 18,
                  fontWeight: 600,
                  letterSpacing: "-0.015em",
                }}
              >
                <Icon name="check-circle" size={20} color={M.ok} />
                First deploy received!
              </div>

              {/* Live progress mini-stepper */}
              <div style={{ width: "100%", maxWidth: 440 }}>
                <Stepper
                  steps={DEPLOY_STEPS}
                  currentIndex={stepIndex}
                  size="mini"
                  terminalState={
                    deployState === "done" && stepIndex === DEPLOY_STEPS.length - 1
                      ? "success"
                      : null
                  }
                />
              </div>

              {/* Countdown */}
              {!cancelRedirect ? (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 12,
                    fontSize: 13,
                    color: M.fg3,
                    fontFamily: M.fontSans,
                  }}
                >
                  <span>
                    Opening Activity in{" "}
                    <motion.span
                      key={countdown}
                      initial={{ opacity: 0, scale: 0.7 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ duration: 0.2 }}
                      style={{ color: M.fg, fontWeight: 600 }}
                    >
                      {countdown}s
                    </motion.span>
                  </span>
                  <button
                    type="button"
                    onClick={handleCancel}
                    style={{
                      padding: "3px 10px",
                      borderRadius: M.rSm,
                      border: `1px solid ${M.line2}`,
                      background: "transparent",
                      color: M.fg3,
                      fontFamily: M.fontSans,
                      fontSize: 12,
                      cursor: "pointer",
                    }}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <Button
                  variant="accent"
                  size="md"
                  trailingIcon="arrow-right"
                  onClick={() => router.push(`/activity?app=${encodeURIComponent(appName)}`)}
                >
                  Open Activity
                </Button>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Navigation */}
      <div style={{ display: "flex", justifyContent: "flex-start" }}>
        <Button variant="ghost" size="md" leadingIcon="arrow-left" onClick={onBack}>
          Back
        </Button>
      </div>
    </div>
  );
}
