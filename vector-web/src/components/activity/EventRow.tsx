"use client";

/**
 * EventRow — a single row in the Activity timeline.
 *
 * Handles both individual events and operationId-grouped deploy lifecycle rows.
 * Grouped rows (multiple events sharing an operationId) collapse into one row
 * with a mini-Stepper showing the deploy lifecycle.
 */

import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useRouter } from "next/navigation";
import { formatDistanceToNow } from "date-fns";
import { M } from "@/design/tokens";
import { Icon } from "@/design/primitives/Icon";
import { Mono } from "@/design/primitives/Mono";
import { Button } from "@/design/primitives/Button";
import { StatusVerb, useStatusVerb } from "@/design/StatusVerb";
import { Stepper } from "@/design/Stepper";
import type { StepperTerminalState, StepItem } from "@/design/Stepper";
import { TimelineRailSegment } from "./TimelineRail";
import type { DeploymentEvent, DeploymentEventType } from "@/types/vector";

// ── Verb → icon mapping (from F1.5 plan table) ─────────────────────────────

const VERB_ICON_MAP: Partial<Record<DeploymentEventType, string>> = {
  DEPLOY_FINISHED: "check",     // overridden to "x" for FAILURE below
  FAILED: "x",
  CRASHED: "x",
  RESTARTED: "rotate-cw",
  STOPPED: "square",
  MANUAL_ROLLBACK: "rotate-ccw",
  UPDATE_AVAILABLE: "arrow-up",
  UPDATE_SUCCESS: "arrow-up",
  UPDATE_TRIGGERED: "arrow-up",
  UPDATE_FAILED: "x",
  UPDATER_UNREACHABLE: "arrow-up",
  PIN_RELEASED: "pin-off",
  // In-flight
  PULL_STARTED: "loader-circle",
  PULL_FINISHED: "loader-circle",
  CONTAINER_CREATING: "loader-circle",
  CONTAINER_STARTED: "loader-circle",
  DEPLOY_TRIGGERED: "loader-circle",
  DEPLOY_STARTED: "loader-circle",
  BUILD_STARTED: "loader-circle",
  BUILD_FINISHED: "loader-circle",
  HEALTH_OK: "check",
};

function resolveIcon(eventType: DeploymentEventType, status?: string | null): string {
  if (eventType === "DEPLOY_FINISHED" && status === "FAILURE") return "x";
  return VERB_ICON_MAP[eventType] ?? "circle";
}

function resolveIconColor(eventType: DeploymentEventType, status?: string | null): string {
  if (eventType === "FAILED" || eventType === "CRASHED") return M.err;
  if (eventType === "DEPLOY_FINISHED" && status === "FAILURE") return M.err;
  if (eventType === "DEPLOY_FINISHED" && status === "SUCCESS") return M.ok;
  if (eventType === "RESTARTED") return M.fg;
  if (eventType === "STOPPED") return M.fg3;
  if (eventType === "MANUAL_ROLLBACK") return M.accent;
  if (
    eventType === "UPDATE_AVAILABLE" ||
    eventType === "UPDATE_SUCCESS" ||
    eventType === "UPDATE_TRIGGERED" ||
    eventType === "UPDATE_FAILED" ||
    eventType === "UPDATER_UNREACHABLE"
  ) return "#67E8F9";
  if (eventType === "PIN_RELEASED") return M.fg3;
  // in-flight / pulling
  return M.warn;
}

// ── Deploy lifecycle stepper helpers ────────────────────────────────────────

const DEPLOY_STEPS: StepItem[] = [
  { id: "triggered", label: "Triggered" },
  { id: "pulling", label: "Pulling" },
  { id: "finished", label: "Finished" },
];

type DeployStepIndex = 0 | 1 | 2;

const IN_FLIGHT_TYPES = new Set<DeploymentEventType>([
  "DEPLOY_TRIGGERED",
  "DEPLOY_STARTED",
  "PULL_STARTED",
  "PULL_FINISHED",
  "CONTAINER_CREATING",
  "CONTAINER_STARTED",
  "BUILD_STARTED",
  "BUILD_FINISHED",
  "HEALTH_OK",
]);

const TERMINAL_TYPES = new Set<DeploymentEventType>([
  "DEPLOY_FINISHED",
  "FAILED",
  "CRASHED",
]);

function getStepperIndex(latestEventType: DeploymentEventType): DeployStepIndex {
  if (latestEventType === "DEPLOY_TRIGGERED" || latestEventType === "DEPLOY_STARTED") return 0;
  if (latestEventType === "PULL_STARTED" || latestEventType === "BUILD_STARTED") return 1;
  if (
    latestEventType === "PULL_FINISHED" ||
    latestEventType === "CONTAINER_CREATING" ||
    latestEventType === "CONTAINER_STARTED" ||
    latestEventType === "BUILD_FINISHED" ||
    latestEventType === "HEALTH_OK"
  ) return 2;
  // Terminal events
  return 2;
}

function getTerminalState(
  eventType: DeploymentEventType,
  status?: string | null,
): "success" | "failure" | null {
  if (eventType === "DEPLOY_FINISHED" && status === "SUCCESS") return "success";
  if (eventType === "DEPLOY_FINISHED" && status === "FAILURE") return "failure";
  if (eventType === "FAILED" || eventType === "CRASHED") return "failure";
  return null;
}

function isDeployLifecycleType(eventType: DeploymentEventType): boolean {
  return IN_FLIGHT_TYPES.has(eventType) || TERMINAL_TYPES.has(eventType);
}

// ── Duration formatter ───────────────────────────────────────────────────────

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(1)}s`;
  const m = Math.floor(s / 60);
  const remaining = Math.round(s % 60);
  return `${m}m ${remaining}s`;
}

// ── Props ────────────────────────────────────────────────────────────────────

export interface EventRowProps {
  /** For an individual event (no operationId grouping) */
  event?: DeploymentEvent;
  /** For a grouped deploy lifecycle (multiple events sharing an operationId) */
  groupedEvents?: DeploymentEvent[];
  isLast?: boolean;
  /** Whether this row just arrived via SSE (triggers pulse aura for 3s) */
  isNew?: boolean;
}

// ── Main component ───────────────────────────────────────────────────────────

export function EventRow({ event, groupedEvents, isLast = false, isNew = false }: EventRowProps) {
  const router = useRouter();
  const [showPulse, setShowPulse] = useState(isNew);
  const pulseTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (isNew) {
      setShowPulse(true);
      pulseTimerRef.current = setTimeout(() => setShowPulse(false), 3000);
    }
    return () => {
      if (pulseTimerRef.current) clearTimeout(pulseTimerRef.current);
    };
  }, [isNew]);

  // Resolve the event to display — prefer grouped, fall back to single
  const displayEvent = groupedEvents
    ? groupedEvents[groupedEvents.length - 1] // latest in the group
    : event;

  if (!displayEvent) return null;

  // For grouped deploy lifecycle rows
  const isGrouped = Boolean(groupedEvents && groupedEvents.length > 1);
  const isDeployGroup =
    isGrouped &&
    groupedEvents!.some((e) => isDeployLifecycleType(e.eventType));

  const latestEventType = displayEvent.eventType;
  const latestStatus = displayEvent.status;

  // Determine the verb for the row — for grouped, use the latest visible event
  const verb = useStatusVerb(latestEventType, latestStatus);

  // For pure lifecycle rows where verb would be hidden (DEPLOY_TRIGGERED/DEPLOY_STARTED
  // standalone), we still render them as part of the stepper group.
  // If verb is null and it's not a grouped lifecycle, skip.
  const isInFlightGroup =
    isDeployGroup &&
    groupedEvents!.every((e) => !TERMINAL_TYPES.has(e.eventType));

  // Stepper state
  const stepperCurrentIndex: DeployStepIndex = isDeployGroup
    ? getStepperIndex(latestEventType)
    : 0;
  const terminalState: "success" | "failure" | null = isDeployGroup
    ? getTerminalState(latestEventType, latestStatus)
    : null;

  // Icon
  const iconName = isInFlightGroup
    ? "loader-circle"
    : resolveIcon(latestEventType, latestStatus);
  const iconColor = isInFlightGroup
    ? M.warn
    : resolveIconColor(latestEventType, latestStatus);

  // If we have no displayable verb and it's not a grouped lifecycle, skip rendering
  if (!verb && !isDeployGroup) return null;

  // Content data
  const sha = displayEvent.commitSha;
  const branch = displayEvent.branch;
  const message = displayEvent.commitMessage;
  const durationMs = displayEvent.durationMs;
  const occurredAt = displayEvent.createdAt;
  const appName = displayEvent.appName;
  const errorMessage = displayEvent.errorMessage;
  const isFailed = latestEventType === "FAILED" || latestEventType === "CRASHED" ||
    (latestEventType === "DEPLOY_FINISHED" && latestStatus === "FAILURE");
  const isCrashed = latestEventType === "CRASHED";

  // Time Machine deep-link
  const timeMachineUrl = `/apps/${encodeURIComponent(appName)}/time-machine?crash=${displayEvent.id}`;

  // Timestamp
  let timeAgo = "";
  try {
    timeAgo = formatDistanceToNow(new Date(occurredAt), { addSuffix: true });
  } catch {
    timeAgo = "";
  }

  return (
    <motion.div
      layout
      style={{ display: "flex", gap: 18, position: "relative" }}
    >
      {/* Rail column — node + vertical line */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          flexShrink: 0,
          width: 28,
        }}
      >
        {/* Node */}
        <div style={{ position: "relative", flexShrink: 0 }}>
          <motion.div
            whileHover={{ scale: 1.08 }}
            style={{
              width: 28,
              height: 28,
              borderRadius: "50%",
              background: M.bg,
              border: `1px solid ${M.line}`,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: iconColor,
              position: "relative",
              zIndex: 1,
            }}
          >
            <Icon
              name={iconName}
              size={13}
              style={
                isInFlightGroup
                  ? {
                      animation: "lp-spin 1.5s linear infinite",
                    }
                  : undefined
              }
            />
          </motion.div>

          {/* Pulse aura for new SSE-arrived rows */}
          <AnimatePresence>
            {showPulse && (
              <motion.span
                key="pulse"
                animate={{
                  scale: [1, 2.2, 1],
                  opacity: [0.6, 0, 0.6],
                }}
                transition={{
                  duration: 2.2,
                  repeat: Infinity,
                  ease: "easeOut",
                }}
                exit={{ opacity: 0, scale: 1 }}
                style={{
                  position: "absolute",
                  inset: -2,
                  borderRadius: "50%",
                  border: `1px solid ${M.accent}`,
                  pointerEvents: "none",
                  zIndex: 0,
                }}
              />
            )}
          </AnimatePresence>
        </div>

        {/* Vertical line below the node */}
        <TimelineRailSegment isLast={isLast} />
      </div>

      {/* Content card */}
      <div
        style={{
          flex: 1,
          minWidth: 0,
          paddingBottom: isLast ? 0 : 24,
        }}
      >
        {/* Top row: verb + app name + duration + timestamp */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            flexWrap: "wrap",
            marginBottom: 8,
          }}
        >
          {/* Verb — use the StatusVerb or a fallback for in-flight groups */}
          {isInFlightGroup ? (
            <span
              style={{
                fontFamily: M.fontMono,
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: "0.08em",
                textTransform: "uppercase",
                color: M.warn,
              }}
            >
              PULLING
            </span>
          ) : verb ? (
            <StatusVerb eventType={latestEventType} status={latestStatus} />
          ) : null}

          <span
            style={{
              fontSize: 13,
              fontWeight: 500,
              color: M.fg,
              letterSpacing: "-0.005em",
              fontFamily: M.fontSans,
            }}
          >
            {appName}
          </span>

          {durationMs != null && (
            <span
              style={{
                fontFamily: M.fontMono,
                fontSize: 11,
                color: M.fg3,
                letterSpacing: "0.02em",
              }}
            >
              · {formatDuration(durationMs)}
            </span>
          )}

          <span
            style={{
              marginLeft: "auto",
              fontSize: 11.5,
              color: M.fg3,
              fontFamily: M.fontSans,
              whiteSpace: "nowrap",
            }}
          >
            {timeAgo}
          </span>
        </div>

        {/* Card body */}
        <motion.div
          whileHover={{ y: -1 }}
          transition={{ duration: 0.15 }}
          style={{
            padding: 16,
            background: M.surface,
            border: `1px solid ${M.line}`,
            borderRadius: M.rMd,
          }}
        >
          {/* SHA · branch · commit message */}
          {(sha || branch || message) && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 8,
                marginBottom: (isFailed || isDeployGroup) ? 12 : 0,
                flexWrap: "wrap",
              }}
            >
              {sha && (
                <Mono style={{ color: M.fg, fontWeight: 500 }}>
                  {sha.slice(0, 7)}
                </Mono>
              )}
              {sha && branch && (
                <span style={{ color: M.fg3, fontSize: 12 }}>·</span>
              )}
              {branch && (
                <Mono
                  style={{
                    color: M.fg2,
                    fontStyle: "italic",
                  }}
                >
                  {branch}
                </Mono>
              )}
              {(sha || branch) && message && (
                <span style={{ color: M.fg3, fontSize: 12 }}>·</span>
              )}
              {message && (
                <span
                  style={{
                    fontSize: 13,
                    color: M.fg2,
                    flex: 1,
                    minWidth: 0,
                    whiteSpace: "nowrap",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    fontFamily: M.fontSans,
                    letterSpacing: "-0.005em",
                  }}
                >
                  {message}
                </span>
              )}
            </div>
          )}

          {/* Deploy lifecycle mini-stepper */}
          {isDeployGroup && (
            <div style={{ marginBottom: isFailed ? 12 : 0 }}>
              <Stepper
                steps={DEPLOY_STEPS}
                currentIndex={stepperCurrentIndex}
                terminalState={terminalState}
                size="mini"
              />
            </div>
          )}

          {/* Error message for FAILED / CRASHED */}
          {isFailed && errorMessage && (
            <pre
              style={{
                padding: 12,
                background: M.bg,
                border: `1px solid ${M.line}`,
                borderRadius: M.rSm,
                fontFamily: M.fontMono,
                fontSize: 12,
                color: M.err,
                lineHeight: 1.6,
                margin: 0,
                whiteSpace: "pre-wrap",
                overflowX: "auto",
              }}
            >
              {isCrashed && displayEvent.errorMessage
                ? displayEvent.errorMessage
                : errorMessage}
            </pre>
          )}

          {isFailed && !errorMessage && (
            <pre
              style={{
                padding: 12,
                background: M.bg,
                border: `1px solid ${M.line}`,
                borderRadius: M.rSm,
                fontFamily: M.fontMono,
                fontSize: 12,
                color: M.err,
                lineHeight: 1.6,
                margin: 0,
              }}
            >
              {isCrashed ? "Container crashed" : "Deploy failed"}
            </pre>
          )}

          {/* Time Machine button for FAILED / CRASHED */}
          {isFailed && (
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 12 }}>
              <Button
                variant="accent"
                size="sm"
                leadingIcon="rewind"
                onClick={() => router.push(timeMachineUrl)}
              >
                Open in Time Machine
              </Button>
            </div>
          )}
        </motion.div>
      </div>
    </motion.div>
  );
}
