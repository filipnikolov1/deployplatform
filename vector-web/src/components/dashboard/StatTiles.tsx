"use client";

/**
 * StatTiles — five horizontal stat tiles.
 * Each tile: uppercase mono kicker + big colored number (counts up on mount).
 * Clicking a tile sets the active filter for the apps table.
 * No icons (per F3.5 drop list).
 */

import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { M, MSTATUS, MMOTION } from "@/design/tokens";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";

export type StatFilter = "all" | "running" | "building" | "failed" | "stopped";

export interface StatCounts {
  all: number;
  running: number;
  building: number;
  failed: number;
  stopped: number;
}

interface StatTilesProps {
  counts: StatCounts;
  activeFilter: StatFilter;
  onFilterChange: (f: StatFilter) => void;
}

const TILES: {
  key: StatFilter;
  label: string;
  color: string;
  delay: number;
}[] = [
  { key: "all",      label: "ALL",      color: M.fg,             delay: 0.05 },
  { key: "running",  label: "RUNNING",  color: MSTATUS.RUNNING.text,  delay: 0.10 },
  { key: "building", label: "BUILDING", color: MSTATUS.BUILDING.text, delay: 0.15 },
  { key: "failed",   label: "FAILED",   color: MSTATUS.FAILED.text,   delay: 0.20 },
  { key: "stopped",  label: "STOPPED",  color: M.fg3,            delay: 0.25 },
];

/** Counts up from 0 to `target` over `duration` ms using rAF. */
function useCountUp(target: number, duration = 600): number {
  const [value, setValue] = useState(0);
  const reduced = usePrefersReducedMotion();
  const rafRef = useRef<number | null>(null);
  const startRef = useRef<number | null>(null);

  useEffect(() => {
    if (reduced) {
      setValue(target);
      return;
    }
    if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    startRef.current = null;

    const animate = (now: number) => {
      if (startRef.current === null) startRef.current = now;
      const elapsed = now - startRef.current;
      const progress = Math.min(elapsed / duration, 1);
      // ease out expo
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(eased * target));
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(animate);
      }
    };

    rafRef.current = requestAnimationFrame(animate);
    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [target, duration, reduced]);

  return value;
}

function StatTile({
  tile,
  value,
  selected,
  onClick,
}: {
  tile: (typeof TILES)[0];
  value: number;
  selected: boolean;
  onClick: () => void;
}) {
  const reduced = usePrefersReducedMotion();
  const displayValue = useCountUp(value);

  return (
    <motion.button
      type="button"
      onClick={onClick}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1], delay: tile.delay }}
      whileHover={reduced ? undefined : { y: -2 }}
      aria-pressed={selected}
      style={{
        position: "relative",
        flex: 1,
        minWidth: 0,
        padding: "20px 22px",
        background: "transparent",
        border: `1px solid ${selected ? M.line3 : M.line}`,
        borderRadius: M.rLg,
        cursor: "pointer",
        textAlign: "left",
        fontFamily: M.fontSans,
        transition: "border-color 200ms",
        outline: "none",
      }}
    >
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: "0.16em",
          textTransform: "uppercase",
          fontFamily: M.fontMono,
          color: selected ? M.fg : M.fg3,
          marginBottom: 12,
          transition: "color 200ms",
        }}
      >
        {tile.label}
      </div>
      <div
        style={{
          fontSize: 36,
          fontWeight: 600,
          color: tile.color,
          letterSpacing: "-0.03em",
          lineHeight: 1,
          fontVariantNumeric: "tabular-nums",
          fontFamily: M.fontSans,
        }}
      >
        {displayValue}
      </div>
    </motion.button>
  );
}

export function StatTiles({ counts, activeFilter, onFilterChange }: StatTilesProps) {
  return (
    <div
      style={{
        display: "flex",
        gap: 12,
        marginBottom: 56,
      }}
    >
      {TILES.map((tile) => (
        <StatTile
          key={tile.key}
          tile={tile}
          value={counts[tile.key]}
          selected={activeFilter === tile.key}
          onClick={() => onFilterChange(tile.key)}
        />
      ))}
    </div>
  );
}
