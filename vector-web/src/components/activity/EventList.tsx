"use client";

/**
 * EventList — animated list of EventRow components.
 *
 * Features:
 * - Groups consecutive events sharing an operationId into a single deploy lifecycle row.
 * - SSE-arrived rows animate in from the top (opacity 0, y -8 → 1, 0) in 250ms.
 * - Existing rows reflow via layout animation.
 * - Empty state: soft message with a single violet pulse on the rail.
 *
 * operationId grouping rules:
 * - Events with null operationId render as individual rows (legacy events).
 * - Events sharing an operationId collapse into ONE row, showing the latest stage.
 * - Groups are ordered by the createdAt of their earliest event (newest first overall).
 */

import { useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import { EventRow } from "./EventRow";
import type { DeploymentEvent } from "@/types/vector";

interface EventListProps {
  events: DeploymentEvent[];
}

interface RowGroup {
  key: string;
  events: DeploymentEvent[];
}

/** Build display groups from the flat events list. Newest first. */
function buildGroups(events: DeploymentEvent[]): RowGroup[] {
  const groups: RowGroup[] = [];
  const opMap = new Map<string, RowGroup>();

  for (const event of events) {
    if (!event.operationId) {
      // No operationId — individual row
      groups.push({ key: `event-${event.id}`, events: [event] });
    } else {
      const existing = opMap.get(event.operationId);
      if (existing) {
        // Add to existing group (newest events come first in the API response,
        // so the group events are newest-first; we push the older event to the end)
        existing.events.push(event);
      } else {
        const group: RowGroup = {
          key: `op-${event.operationId}`,
          events: [event],
        };
        opMap.set(event.operationId, group);
        groups.push(group);
      }
    }
  }

  return groups;
}

// Empty state component
function EmptyStateRail() {
  return (
    <div
      style={{
        display: "flex",
        gap: 18,
        paddingTop: 8,
      }}
    >
      {/* Rail column with pulse */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          flexShrink: 0,
          width: 28,
        }}
      >
        <div style={{ position: "relative" }}>
          <div
            style={{
              width: 28,
              height: 28,
              borderRadius: "50%",
              background: M.bg,
              border: `1px solid ${M.line}`,
              zIndex: 1,
              position: "relative",
            }}
          />
          {/* Single violet pulse */}
          <motion.span
            animate={{
              scale: [1, 2.2, 1],
              opacity: [0.6, 0, 0.6],
            }}
            transition={{
              duration: 2.2,
              repeat: Infinity,
              ease: "easeOut",
            }}
            style={{
              position: "absolute",
              inset: -2,
              borderRadius: "50%",
              border: `1px solid ${M.accent}`,
              pointerEvents: "none",
              zIndex: 0,
            }}
          />
        </div>
      </div>

      {/* Empty state message */}
      <div
        style={{
          paddingTop: 4,
          color: M.fg3,
          fontSize: 13,
          fontFamily: M.fontSans,
          lineHeight: 1.6,
          letterSpacing: "-0.005em",
        }}
      >
        No deploys yet. Push a commit to your connected repo to see something here.
      </div>
    </div>
  );
}

export function EventList({ events }: EventListProps) {
  const prevIdsRef = useRef<Set<string>>(new Set());
  const groups = buildGroups(events);

  // Determine which group keys are new (just arrived via SSE)
  const currentKeys = new Set(groups.map((g) => g.key));
  const newKeys = new Set<string>();
  groups.forEach((g) => {
    if (!prevIdsRef.current.has(g.key)) {
      newKeys.add(g.key);
    }
  });
  // Update the ref after computing newKeys (we don't want to trigger a re-render)
  prevIdsRef.current = currentKeys;

  if (groups.length === 0) {
    return <EmptyStateRail />;
  }

  return (
    <AnimatePresence initial={false}>
      {groups.map((group, idx) => {
        const isLast = idx === groups.length - 1;
        const isNew = newKeys.has(group.key);
        const isSingle = group.events.length === 1;

        return (
          <motion.div
            key={group.key}
            layout
            initial={isNew ? { opacity: 0, y: -8 } : false}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
          >
            {isSingle ? (
              <EventRow
                event={group.events[0]}
                isLast={isLast}
                isNew={isNew}
              />
            ) : (
              <EventRow
                groupedEvents={group.events}
                isLast={isLast}
                isNew={isNew}
              />
            )}
          </motion.div>
        );
      })}
    </AnimatePresence>
  );
}
