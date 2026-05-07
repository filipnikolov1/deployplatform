"use client";

/**
 * TimelineRail — vertical 1px line down the left side at a fixed x offset.
 * Each event row positions a 28px circle node aligned to that line.
 * The outer container (EventRow) handles the actual node rendering;
 * this component provides the continuous vertical line behind all rows.
 */

import { M } from "@/design/tokens";

interface TimelineRailProps {
  /** Whether this is the last item in the list (omits the trailing line segment) */
  isLast?: boolean;
  children: React.ReactNode;
}

/** The x-center of the rail node column, in px from the left of the row container. */
export const RAIL_X = 28;

/** Width of the node circle. */
export const NODE_SIZE = 28;

/**
 * TimelineRailSegment — renders the vertical line segment below a node.
 * Used inside EventRow to draw the connector between consecutive rows.
 */
export function TimelineRailSegment({ isLast }: { isLast: boolean }) {
  if (isLast) return null;
  return (
    <div
      style={{
        flex: 1,
        width: 1,
        background: M.line,
        minHeight: 20,
        marginTop: 4,
      }}
    />
  );
}

/**
 * TimelineRailWrapper — outer container that provides the row layout
 * (rail column on the left, content on the right).
 */
export function TimelineRailWrapper({ isLast, children }: TimelineRailProps) {
  return (
    <div
      style={{
        display: "flex",
        gap: 18,
        position: "relative",
        paddingBottom: isLast ? 0 : 0,
      }}
    >
      {children}
    </div>
  );
}
