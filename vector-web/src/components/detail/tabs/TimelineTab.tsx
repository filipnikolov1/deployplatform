"use client";

/**
 * TimelineTab — F5.5
 * Two filter pills: All | Deploys
 * All view: reuses EventList (F4) filtered to this app.
 * Deploys view: DEPLOY_FINISHED events with availability dot + Roll back button.
 */

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useSWRConfig } from "swr";
import { RotateCcw, Clock } from "lucide-react";
import { M } from "@/design/tokens";
import { Button } from "@/design/primitives/Button";
import { StatusVerb } from "@/design/StatusVerb";
import { Mono } from "@/design/primitives/Mono";
import { EventList } from "@/components/activity/EventList";
import { RollbackConfirmDialog } from "../RollbackConfirmDialog";
import { toast } from "@/lib/toast";
import type { DeploymentEvent } from "@/types/vector";

type TimelineFilter = "all" | "deploys";

const FILTER_TABS: { id: TimelineFilter; label: string }[] = [
  { id: "all", label: "All" },
  { id: "deploys", label: "Deploys" },
];

interface FilterPillProps {
  active: boolean;
  layoutId: string;
  onClick: () => void;
  children: React.ReactNode;
}

function FilterPill({ active, layoutId, onClick, children }: FilterPillProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        position: "relative",
        padding: "6px 14px",
        borderRadius: M.rPill,
        fontSize: 12,
        fontFamily: M.fontSans,
        fontWeight: 500,
        cursor: "pointer",
        border: "none",
        background: "transparent",
        color: active ? M.fg : M.fg3,
        outline: "none",
        transition: "color 150ms",
        letterSpacing: "-0.005em",
      }}
    >
      {active && (
        <motion.span
          layoutId={layoutId}
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: M.rPill,
            background: M.surface2,
            border: `1px solid ${M.line2}`,
            zIndex: 0,
          }}
          transition={{ type: "spring", stiffness: 380, damping: 32 }}
        />
      )}
      <span style={{ position: "relative", zIndex: 1 }}>{children}</span>
    </button>
  );
}

interface DeployRowProps {
  event: DeploymentEvent;
  isCurrent: boolean;
  currentImage?: string | null;
  onRollback: (event: DeploymentEvent) => void;
}

function DeployRow({ event, isCurrent, onRollback }: DeployRowProps) {
  const locallyAvailable = event.availableLocally === true;
  const isSuccess = event.status === "SUCCESS";

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: -4 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -4 }}
      transition={{ duration: 0.22 }}
      style={{
        padding: "12px 14px",
        borderRadius: M.rMd,
        border: isCurrent ? `1px solid ${M.accentLine}` : `1px solid ${M.line}`,
        background: isCurrent ? M.accentSoft : M.surface,
        display: "flex",
        flexDirection: "column",
        gap: 8,
      }}
    >
      {/* Top row: verb + sha + branch + message + current chip */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          flexWrap: "wrap",
          minWidth: 0,
        }}
      >
        <StatusVerb eventType={event.eventType} status={event.status} />

        {event.commitSha && (
          <Mono style={{ color: M.fg, fontWeight: 500 }}>
            {event.commitSha.slice(0, 7)}
          </Mono>
        )}

        {event.branch && (
          <>
            <span style={{ color: M.fg3, fontSize: 12 }}>·</span>
            <Mono style={{ color: M.fg2, fontStyle: "italic" }}>{event.branch}</Mono>
          </>
        )}

        {event.commitMessage && (
          <>
            <span style={{ color: M.fg3, fontSize: 12 }}>·</span>
            <span
              style={{
                flex: 1,
                minWidth: 0,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                fontSize: 12.5,
                fontFamily: M.fontSans,
                color: M.fg2,
                letterSpacing: "-0.005em",
              }}
              title={event.commitMessage}
            >
              {event.commitMessage}
            </span>
          </>
        )}

        {isCurrent && (
          <span
            style={{
              padding: "2px 8px",
              borderRadius: M.rPill,
              fontSize: 10,
              fontFamily: M.fontMono,
              fontWeight: 700,
              letterSpacing: "0.08em",
              textTransform: "uppercase",
              color: M.accentLight,
              background: M.accentSoft,
              border: `1px solid ${M.accentLine}`,
              flexShrink: 0,
            }}
          >
            current
          </span>
        )}
      </div>

      {/* Bottom row: availability dot + timestamp + roll back */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          flexWrap: "wrap",
        }}
      >
        {/* Image availability dot */}
        <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
          <div
            style={{
              width: 6,
              height: 6,
              borderRadius: "50%",
              background: locallyAvailable ? M.ok : M.fg4,
              flexShrink: 0,
            }}
            title={locallyAvailable ? "Image cached locally — rollback is instant" : "Image not cached locally"}
          />
          <span
            style={{
              fontSize: 11,
              fontFamily: M.fontMono,
              color: locallyAvailable ? M.ok : M.fg4,
            }}
          >
            {locallyAvailable ? "local" : "remote"}
          </span>
        </div>

        {/* Duration */}
        {event.durationMs != null && (
          <span
            style={{
              fontSize: 11,
              fontFamily: M.fontMono,
              color: M.fg3,
            }}
          >
            {(event.durationMs / 1000).toFixed(1)}s
          </span>
        )}

        {/* Timestamp */}
        <span style={{ display: "flex", alignItems: "center", gap: 4, marginLeft: "auto" }}>
          <Clock style={{ width: 11, height: 11, color: M.fg3 }} />
          <time
            dateTime={event.createdAt}
            style={{ fontSize: 11, fontFamily: M.fontMono, color: M.fg3 }}
          >
            {new Date(event.createdAt).toLocaleString()}
          </time>
        </span>

        {/* Roll back button */}
        {!isCurrent && isSuccess && (
          <button
            type="button"
            onClick={() => onRollback(event)}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 5,
              padding: "4px 10px",
              borderRadius: M.rPill,
              fontSize: 11,
              fontFamily: M.fontSans,
              fontWeight: 500,
              cursor: "pointer",
              border: `1px solid ${M.accentLine}`,
              background: M.accentSoft,
              color: M.accentLight,
              outline: "none",
              flexShrink: 0,
            }}
          >
            <RotateCcw style={{ width: 11, height: 11 }} />
            Roll back to this
          </button>
        )}
      </div>
    </motion.div>
  );
}

interface TimelineTabProps {
  appName: string;
  events: DeploymentEvent[];
  currentImage?: string | null;
}

export function TimelineTab({ appName, events, currentImage }: TimelineTabProps) {
  const [filter, setFilter] = useState<TimelineFilter>("all");
  const [rollbackTarget, setRollbackTarget] = useState<DeploymentEvent | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const { mutate } = useSWRConfig();

  const deployEvents = events.filter(
    (e) =>
      (e.eventType === "DEPLOY_FINISHED" || e.eventType === "MANUAL_ROLLBACK") &&
      !!e.imageName
  );

  const performRollback = async (event: DeploymentEvent) => {
    setSubmitting(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(appName)}/rollback`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ eventId: event.id }),
        }
      );
      if (res.status === 409) {
        toast.error("App is busy — try again in a few seconds");
        return;
      }
      if (!res.ok) {
        toast.error("Rollback failed");
        return;
      }
      toast.success("Rolled back");
      void mutate("/api/apps");
      void mutate(`/api/apps/${encodeURIComponent(appName)}`);
      void mutate(
        (key) =>
          typeof key === "string" &&
          key.startsWith(`/api/apps/${encodeURIComponent(appName)}/events`)
      );
      setRollbackTarget(null);
    } catch {
      toast.error("Rollback failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      {/* Filter pills */}
      <div
        style={{
          display: "flex",
          gap: 2,
          padding: 3,
          background: M.surface,
          border: `1px solid ${M.line}`,
          borderRadius: M.rPill,
          width: "fit-content",
        }}
      >
        {FILTER_TABS.map((t) => (
          <FilterPill
            key={t.id}
            active={filter === t.id}
            layoutId="drawer-timeline-filter"
            onClick={() => setFilter(t.id)}
          >
            {t.label}
          </FilterPill>
        ))}
      </div>

      {/* Content */}
      <AnimatePresence mode="wait">
        {filter === "all" ? (
          <motion.div
            key="all"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
          >
            <EventList events={events} />
          </motion.div>
        ) : (
          <motion.div
            key="deploys"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
          >
            {deployEvents.length === 0 ? (
              <div
                style={{
                  color: M.fg3,
                  fontSize: 13,
                  fontFamily: M.fontSans,
                  padding: "20px 0",
                }}
              >
                No deploy history yet.
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                <AnimatePresence initial={false}>
                  {deployEvents.map((event) => (
                    <DeployRow
                      key={event.id}
                      event={event}
                      isCurrent={!!currentImage && event.imageName === currentImage}
                      currentImage={currentImage}
                      onRollback={setRollbackTarget}
                    />
                  ))}
                </AnimatePresence>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Rollback confirm dialog */}
      {rollbackTarget && (
        <RollbackConfirmDialog
          appName={appName}
          event={rollbackTarget}
          submitting={submitting}
          onCancel={() => setRollbackTarget(null)}
          onConfirm={() => performRollback(rollbackTarget)}
        />
      )}
    </div>
  );
}
