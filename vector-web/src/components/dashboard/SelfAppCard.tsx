"use client";

/**
 * SelfAppCard — card for apps where isSelfApp === true.
 *
 * Layout:
 *   Top row  : app name + <StatusChip> top-right
 *   Sub-row  : shortened SHA in Mono
 *   Bottom   : time-ago left | Update button / Up-to-date text + Time Machine icon right
 *   Progress : <OperationProgress> bar slides in under card body when update is in-flight.
 *
 * Hover: y: -3 spring + soft violet radial-gradient glow pinned to cursor position.
 */

import { useState, useCallback, useEffect, useRef } from "react";
import { useSWRConfig } from "swr";
import { motion, AnimatePresence } from "framer-motion";
import { useRouter } from "next/navigation";
import { formatDistanceToNow } from "date-fns";
import { M, MSTATUS, MMOTION } from "@/design/tokens";
import { StatusChip } from "@/design/primitives/StatusChip";
import { Button } from "@/design/primitives/Button";
import { Icon } from "@/design/primitives/Icon";
import { Mono } from "@/design/primitives/Mono";
import { OperationProgress } from "@/design/OperationProgress";
import { toast } from "@/lib/toast";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";
import { toAppStatus } from "@/lib/statusConfig";
import type { Deployment } from "@/types/deployment";
import type { DeploymentEvent, DeploymentEventType } from "@/types/vector";

const IN_PROGRESS_TYPES = new Set<DeploymentEventType>([
  "UPDATE_TRIGGERED",
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
  "UPDATE_SUCCESS",
  "UPDATE_FAILED",
  "UPDATER_UNREACHABLE",
  "FAILED",
  "CRASHED",
]);

function findActiveOperationId(appName: string, events: DeploymentEvent[]): string | null {
  // events is newest-first. Only the latest event decides whether an operation is
  // still in flight — if the latest event for this app isn't in-progress, no operation
  // is active, even if older events look incomplete (legacy rows had NULL opIds on
  // their terminal events, which would otherwise trick a full scan into returning a
  // stale opId forever).
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
}

interface SelfAppCardProps {
  app: Deployment;
  onOpen: (appName: string) => void;
  updateAvailableEvent?: DeploymentEvent | null;
  events: DeploymentEvent[];
}

export function SelfAppCard({ app, onOpen, updateAvailableEvent, events }: SelfAppCardProps) {
  const reduced = usePrefersReducedMotion();
  const { mutate } = useSWRConfig();
  const router = useRouter();

  const [hover, setHover] = useState(false);
  const [glowPos, setGlowPos] = useState({ x: "50%", y: "0%" });
  const [busy, setBusy] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  // operationId is derived from the events feed — works for both manual click
  // (event arrives via SSE shortly after the POST) and auto-trigger (webhook
  // path, no click ever happens).
  const operationId = findActiveOperationId(app.appName, events);
  const prevOperationIdRef = useRef<string | null>(null);
  useEffect(() => {
    // When an in-flight operation transitions to terminal (operationId becomes null
    // after being set), refetch /api/apps so the row picks up the new image/sha.
    if (prevOperationIdRef.current && !operationId) {
      void mutate("/api/apps");
    }
    prevOperationIdRef.current = operationId;
  }, [operationId, mutate]);

  const appStatus = toAppStatus(app.status);
  // Map DeploymentStatus to MStatus for StatusChip
  const mStatus = ((): keyof typeof MSTATUS => {
    switch (appStatus) {
      case "RUNNING":  return "RUNNING";
      case "BUILDING": return "BUILDING";
      case "FAILED":   return "FAILED";
      case "CRASHED":  return "CRASHED";
      case "STOPPED":  return "STOPPED";
      case "PENDING":  return "PENDING";
    }
  })();

  const hasUpdate = !!updateAvailableEvent || (
    !!app.latestKnownSha && !!app.commitSha && app.latestKnownSha !== app.commitSha
  );

  // Manual-update path: vector-api always (kill-self problem), plus any self-app
  // the user has pinned. Auto-update self-apps (unpinned, non-API) deploy themselves
  // on webhook so no button is needed. vector-updater can't recreate itself via the
  // updater binary, so we just surface "manual rebuild" status without a button.
  const isManualUpdate = app.appName === "vector-api" || !!app.pinnedImage;
  const isUpdaterSelf = app.appName === "vector-updater";
  const showUpdateButton = isManualUpdate && hasUpdate && !isUpdaterSelf;

  const updatedAt = app.updatedAt
    ? formatDistanceToNow(new Date(app.updatedAt), { addSuffix: true })
    : "—";

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      if (!cardRef.current || reduced) return;
      const rect = cardRef.current.getBoundingClientRect();
      const x = ((e.clientX - rect.left) / rect.width) * 100;
      const y = ((e.clientY - rect.top) / rect.height) * 100;
      setGlowPos({ x: `${x}%`, y: `${y}%` });
    },
    [reduced],
  );

  const handleUpdate = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (busy) return;
    setBusy(true);
    try {
      const res = await fetch(
        `/api/self-apps/${encodeURIComponent(app.appName)}/update`,
        { method: "POST" },
      );
      if (!res.ok) {
        toast.error("Update failed");
        return;
      }
      toast.success("Update started");
    } catch {
      toast.error("Update failed");
    } finally {
      setBusy(false);
    }
  };

  const handleTimeMachine = (e: React.MouseEvent) => {
    e.stopPropagation();
    router.push(`/apps/${encodeURIComponent(app.appName)}/time-machine`);
  };

  return (
    <motion.div
      ref={cardRef}
      variants={MMOTION.item}
      whileHover={reduced ? undefined : { y: -3 }}
      transition={{ type: "spring", stiffness: 300, damping: 24 }}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onMouseMove={handleMouseMove}
      onClick={() => onOpen(app.appName)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen(app.appName);
        }
      }}
      style={{
        display: "block",
        textAlign: "left",
        padding: 20,
        background: M.surface,
        border: `1px solid ${M.line}`,
        borderRadius: M.rLg,
        cursor: "pointer",
        fontFamily: M.fontSans,
        position: "relative",
        overflow: "hidden",
        outline: "none",
      }}
    >
      {/* Cursor-tracking violet glow */}
      {!reduced && (
        <motion.div
          animate={{ opacity: hover ? 1 : 0 }}
          transition={{ duration: 0.25 }}
          style={{
            position: "absolute",
            inset: 0,
            pointerEvents: "none",
            background: `radial-gradient(circle at ${glowPos.x} ${glowPos.y}, ${M.accentSoft}, transparent 60%)`,
          }}
        />
      )}

      {/* Top row: name + status chip */}
      <div
        style={{
          position: "relative",
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: 12,
          marginBottom: 6,
        }}
      >
        <div style={{ minWidth: 0, flex: 1 }}>
          <div
            style={{
              fontSize: 14.5,
              fontWeight: 600,
              color: M.fg,
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
              letterSpacing: "-0.01em",
            }}
          >
            {app.appName}
          </div>
          {app.commitSha && (
            <Mono
              style={{
                fontSize: 11.5,
                color: M.fg3,
                marginTop: 4,
                display: "block",
              }}
            >
              {app.commitSha.slice(0, 7)}
            </Mono>
          )}
        </div>
        <StatusChip status={mStatus} compact />
      </div>

      {/* Bottom row: time-ago | update / up-to-date + time machine */}
      <motion.div
        animate={{ y: operationId ? -10 : 0 }}
        transition={{ type: "spring", stiffness: 380, damping: 32 }}
        style={{
          position: "relative",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          fontSize: 12,
          color: M.fg2,
          marginTop: 12,
          minHeight: 28,
        }}
      >
        <span>{updatedAt}</span>

        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
          <AnimatePresence mode="wait" initial={false}>
            {operationId ? (
              <motion.span
                key="stage"
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.18 }}
                style={{
                  fontFamily: M.fontMono,
                  fontSize: 10.5,
                  color: M.accentLight,
                }}
              >
                Updating…
              </motion.span>
            ) : showUpdateButton ? (
              <motion.button
                key="update"
                type="button"
                onClick={handleUpdate}
                disabled={busy}
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                whileHover={reduced ? undefined : { y: -1, background: M.accentSoft }}
                whileTap={reduced ? undefined : { scale: 0.97 }}
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 4,
                  color: M.accentLight,
                  fontWeight: 500,
                  cursor: busy ? "not-allowed" : "pointer",
                  padding: "3px 9px",
                  borderRadius: 999,
                  border: `1px solid ${M.accent}`,
                  fontSize: 11.5,
                  fontFamily: M.fontSans,
                  background: "transparent",
                  opacity: busy ? 0.6 : 1,
                  outline: "none",
                }}
              >
                <Icon name="arrow-up" size={11} />
                Update
              </motion.button>
            ) : (
              <span key="utd" style={{ color: M.fg3 }}>
                {hasUpdate
                  ? isUpdaterSelf
                    ? "Rebuild from compose"
                    : "Auto-updating…"
                  : "Up to date"}
              </span>
            )}
          </AnimatePresence>

          {/* Time Machine button */}
          {!operationId && (
            <Button
              variant="icon"
              leadingIcon="rewind"
              title="Open in Time Machine"
              onClick={handleTimeMachine}
              style={{
                width: 28,
                height: 28,
                borderRadius: M.rSm,
                opacity: hover ? 1 : 0.5,
                transition: "opacity 150ms",
              }}
            />
          )}
        </span>
      </motion.div>

      {/* Progress bar pinned to card bottom */}
      <AnimatePresence>
        {operationId && (
          <motion.div
            key="bar"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 4 }}
            exit={{ opacity: 0, height: 0, transition: { duration: 0.25 } }}
            transition={{ type: "spring", stiffness: 380, damping: 32 }}
            style={{
              position: "relative",
              marginTop: 14,
              overflow: "hidden",
            }}
          >
            <OperationProgress operationId={operationId} variant="inline" />
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
