"use client";

/**
 * AppsTable — all-apps table.
 *
 * Columns: NAME (with status dot) | IMAGE | BRANCH | PORT | UPDATED
 * No STATUS column — status is conveyed by the leading dot color (MSTATUS).
 * Hover row: background tint + reveals trailing Time Machine icon button.
 * Click row body → sets ?app=name query param to open the drawer.
 * In-flight deploy: 2px progress bar absolute-positioned at row bottom.
 * Rows stagger in on initial load.
 */

import { useState } from "react";
import { useRouter, usePathname, useSearchParams } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { formatDistanceToNow } from "date-fns";
import { M, MSTATUS, MMOTION } from "@/design/tokens";
import { Button } from "@/design/primitives/Button";
import { Mono } from "@/design/primitives/Mono";
import { Icon } from "@/design/primitives/Icon";
import { OperationProgress } from "@/design/OperationProgress";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";
import { getDisplayImageName } from "@/lib/image-display";
import { toAppStatus } from "@/lib/statusConfig";
import type { Deployment } from "@/types/deployment";
import type { DeploymentEvent, DeploymentEventType } from "@/types/vector";

const IN_PROGRESS_TYPES = new Set<DeploymentEventType>([
  "DEPLOY_TRIGGERED",
  "PULL_STARTED",
  "PULL_FINISHED",
  "CONTAINER_CREATING",
  "CONTAINER_STARTED",
  "DEPLOY_STARTED",
]);

const TERMINAL_TYPES = new Set<DeploymentEventType>([
  "DEPLOY_FINISHED",
  "HEALTH_OK",
  "FAILED",
  "CRASHED",
]);

interface AppsTableProps {
  apps: Deployment[];
  /** All events (workspace-wide) to check for in-flight deploy operations. */
  events: DeploymentEvent[];
}

interface AppRowProps {
  app: Deployment;
  onOpen: (appName: string) => void;
  onTimeMachine: (appName: string) => void;
  activeOperationId: string | null;
}

function AppRow({ app, onOpen, onTimeMachine, activeOperationId }: AppRowProps) {
  const [hover, setHover] = useState(false);
  const reduced = usePrefersReducedMotion();

  const appStatus = toAppStatus(app.status);
  // Map to MStatus key
  const mStatusKey = ((): keyof typeof MSTATUS => {
    switch (appStatus) {
      case "RUNNING":  return "RUNNING";
      case "BUILDING": return "BUILDING";
      case "FAILED":   return "FAILED";
      case "CRASHED":  return "CRASHED";
      case "STOPPED":  return "STOPPED";
      case "PENDING":  return "PENDING";
    }
  })();

  const s = MSTATUS[mStatusKey];
  const displayImage = getDisplayImageName(app);
  const updatedAt = app.updatedAt
    ? formatDistanceToNow(new Date(app.updatedAt), { addSuffix: true })
    : "—";

  return (
    <motion.tr
      variants={MMOTION.item}
      onClick={() => onOpen(app.appName)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        cursor: "pointer",
        borderBottom: `1px solid ${M.line}`,
        background: hover || activeOperationId ? M.surface2 : "transparent",
        transition: "background 150ms",
        position: "relative",
      }}
    >
      {/* NAME with status dot */}
      <td style={{ padding: "16px 18px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span
            style={{ position: "relative", display: "inline-flex", width: 6, height: 6 }}
          >
            {(appStatus === "RUNNING" || appStatus === "BUILDING") && !reduced && (
              <motion.span
                animate={{ opacity: [0.5, 0, 0.5], scale: [1, 2.2, 1] }}
                transition={{ duration: 2.4, repeat: Infinity, ease: "easeOut" }}
                style={{
                  position: "absolute",
                  inset: 0,
                  borderRadius: "50%",
                  background: s.dot,
                }}
              />
            )}
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
          <span
            style={{
              fontSize: 14,
              fontWeight: 500,
              color: M.fg,
              letterSpacing: "-0.005em",
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
              maxWidth: 200,
            }}
          >
            {app.appName}
          </span>
        </div>
      </td>

      {/* IMAGE */}
      <td style={{ padding: "16px 18px" }}>
        <Mono style={{ color: M.fg2, fontSize: 12 }}>{displayImage}</Mono>
      </td>

      {/* BRANCH */}
      <td style={{ padding: "16px 18px" }}>
        {app.branch ? (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 6,
              fontSize: 12,
              color: M.fg2,
              fontFamily: M.fontMono,
            }}
          >
            <Icon name="git-branch" size={11} />
            {app.branch}
          </span>
        ) : (
          <Mono style={{ color: M.fg4 }}>—</Mono>
        )}
      </td>

      {/* PORT */}
      <td style={{ padding: "16px 18px" }}>
        <Mono
          style={{
            color: M.fg2,
            fontSize: 12,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          :{app.containerPort}
        </Mono>
      </td>

      {/* UPDATED */}
      <td
        style={{
          padding: "16px 18px",
          fontSize: 12,
          color: M.fg3,
          whiteSpace: "nowrap",
          fontFamily: M.fontSans,
        }}
      >
        {updatedAt}
      </td>

      {/* Actions cell — Time Machine revealed on hover */}
      <td
        style={{
          padding: "16px 18px",
          textAlign: "right",
          width: 56,
          position: "relative",
        }}
      >
        <AnimatePresence>
          {hover && (
            <motion.div
              key="tm-btn"
              initial={{ opacity: 0, x: -4 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -4 }}
              transition={{ duration: 0.15 }}
              style={{ display: "inline-flex", justifyContent: "flex-end" }}
            >
              <Button
                variant="icon"
                leadingIcon="rewind"
                title="Time Machine"
                onClick={(e) => {
                  e.stopPropagation();
                  onTimeMachine(app.appName);
                }}
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: M.rSm,
                  border: `1px solid ${M.line2}`,
                }}
              />
            </motion.div>
          )}
        </AnimatePresence>

        {/* In-flight progress bar at row bottom */}
        <AnimatePresence>
          {activeOperationId && (
            <motion.div
              key="row-progress"
              initial={{ opacity: 0, scaleX: 0.6 }}
              animate={{ opacity: 1, scaleX: 1 }}
              exit={{ opacity: 0, transition: { duration: 0.2 } }}
              transition={{ type: "spring", stiffness: 260, damping: 28 }}
              style={{
                transformOrigin: "left center",
                position: "absolute",
                left: 18,
                right: 18,
                bottom: 2,
                height: 2,
              }}
            >
              <OperationProgress
                operationId={activeOperationId}
                variant="inline"
              />
            </motion.div>
          )}
        </AnimatePresence>
      </td>
    </motion.tr>
  );
}

const HEADERS = ["NAME", "IMAGE", "BRANCH", "PORT", "UPDATED", ""] as const;

export function AppsTable({ apps, events }: AppsTableProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const handleOpen = (appName: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("app", appName);
    router.push(`${pathname}?${params.toString()}`);
  };

  const handleTimeMachine = (appName: string) => {
    router.push(`/apps/${encodeURIComponent(appName)}/time-machine`);
  };

  /** Resolve active operationId for a given app from the events feed. */
  const getActiveOperationId = (appName: string): string | null => {
    const appEvents = events.filter((e) => e.appName === appName);
    if (!appEvents.length) return null;
    const latest = appEvents[0];
    if (!latest.operationId) return null;
    if (!IN_PROGRESS_TYPES.has(latest.eventType)) return null;
    const opId = latest.operationId;
    const hasTerminal = appEvents.some(
      (e) => e.operationId === opId && TERMINAL_TYPES.has(e.eventType),
    );
    return hasTerminal ? null : opId;
  };

  return (
    <div
      style={{
        border: `1px solid ${M.line}`,
        borderRadius: M.rLg,
        overflow: "hidden",
        background: M.surface,
      }}
    >
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            {HEADERS.map((h) => (
              <th
                key={h}
                style={{
                  padding: "12px 18px",
                  textAlign: "left",
                  fontSize: 10.5,
                  fontWeight: 600,
                  color: M.fg3,
                  textTransform: "uppercase",
                  letterSpacing: "0.14em",
                  borderBottom: `1px solid ${M.line}`,
                  fontFamily: M.fontMono,
                  whiteSpace: "nowrap",
                }}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <motion.tbody variants={MMOTION.list} initial="initial" animate="animate">
          {apps.length === 0 ? (
            <tr>
              <td
                colSpan={HEADERS.length}
                style={{
                  padding: 60,
                  textAlign: "center",
                  color: M.fg3,
                  fontFamily: M.fontSans,
                  fontSize: 14,
                }}
              >
                No apps match.
              </td>
            </tr>
          ) : (
            apps.map((app) => (
              <AppRow
                key={app.appName}
                app={app}
                onOpen={handleOpen}
                onTimeMachine={handleTimeMachine}
                activeOperationId={getActiveOperationId(app.appName)}
              />
            ))
          )}
        </motion.tbody>
      </table>
    </div>
  );
}
