"use client";

import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { M, MMOTION } from "@/design/tokens";
import { formatDistanceToNow } from "date-fns";

interface StatTile {
  label: string;
  rawValue: string;
  numericValue: number | null;
  accent: string;
}

interface Props {
  uptimePercent: number | null;
  deploysCount: number | null;
  lastCrashAt: Date | null;
  avgPullMs: number | null;
  isLoading?: boolean;
}

function useCountUp(target: number, duration = 600): number {
  const [value, setValue] = useState(0);
  const rafRef = useRef<number | null>(null);
  const startRef = useRef<number>(0);

  useEffect(() => {
    if (target === 0) {
      setValue(0);
      return;
    }
    startRef.current = performance.now();
    const tick = (now: number) => {
      const elapsed = now - startRef.current;
      const progress = Math.min(elapsed / duration, 1);
      // ease out
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(eased * target));
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick);
      }
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
    };
  }, [target, duration]);

  return value;
}

function CountUpNumber({
  target,
  suffix = "",
}: {
  target: number;
  suffix?: string;
}) {
  const value = useCountUp(target);
  return (
    <span>
      {value}
      {suffix}
    </span>
  );
}

function buildTiles(
  uptimePercent: number | null,
  deploysCount: number | null,
  lastCrashAt: Date | null,
  avgPullMs: number | null
): StatTile[] {
  const avgPullSec = avgPullMs != null ? Math.round(avgPullMs / 1000) : null;

  return [
    {
      label: "UPTIME 30D",
      rawValue: uptimePercent != null ? `${uptimePercent.toFixed(1)}%` : "—",
      numericValue: uptimePercent,
      accent: M.ok,
    },
    {
      label: "DEPLOYS 30D",
      rawValue: deploysCount != null ? String(deploysCount) : "—",
      numericValue: deploysCount,
      accent: M.fg,
    },
    {
      label: "LAST CRASH",
      rawValue:
        lastCrashAt != null
          ? formatDistanceToNow(lastCrashAt, { addSuffix: true })
          : "None",
      numericValue: null,
      accent: M.fg2,
    },
    {
      label: "AVG PULL",
      rawValue: avgPullSec != null ? `${avgPullSec}s` : "—",
      numericValue: avgPullSec,
      accent: M.fg,
    },
  ];
}

export function HealthyStats({
  uptimePercent,
  deploysCount,
  lastCrashAt,
  avgPullMs,
  isLoading = false,
}: Props) {
  const tiles = buildTiles(uptimePercent, deploysCount, lastCrashAt, avgPullMs);

  if (isLoading) {
    return (
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: 12,
          marginBottom: 32,
        }}
      >
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            style={{
              height: 88,
              borderRadius: M.rLg,
              background: M.surface,
              border: `1px solid ${M.line}`,
            }}
          />
        ))}
      </div>
    );
  }

  return (
    <motion.div
      variants={MMOTION.list}
      initial="initial"
      animate="animate"
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(4, 1fr)",
        gap: 12,
        marginBottom: 32,
      }}
    >
      {tiles.map((tile) => (
        <motion.div
          key={tile.label}
          variants={MMOTION.item}
          style={{
            padding: "20px 22px",
            border: `1px solid ${M.line}`,
            borderRadius: M.rLg,
            background: M.surface,
          }}
        >
          <div
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: M.fg3,
              letterSpacing: "0.16em",
              textTransform: "uppercase",
              marginBottom: 12,
              fontFamily: M.fontSans,
            }}
          >
            {tile.label}
          </div>
          <div
            style={{
              fontSize: 28,
              fontWeight: 600,
              color: tile.accent,
              letterSpacing: "-0.02em",
              fontVariantNumeric: "tabular-nums",
              fontFamily: M.fontSans,
              lineHeight: 1,
            }}
          >
            {tile.numericValue != null ? (
              tile.label === "UPTIME 30D" ? (
                // Display as "99.8%" — count up integer part only, append decimal statically
                <span>
                  <CountUpNumber target={Math.floor(tile.numericValue)} />
                  <span style={{ fontSize: 18, fontWeight: 400, color: tile.accent, opacity: 0.75 }}>
                    .{String(Math.round((tile.numericValue % 1) * 10))}%
                  </span>
                </span>
              ) : tile.label === "AVG PULL" ? (
                <CountUpNumber target={tile.numericValue} suffix="s" />
              ) : (
                <CountUpNumber target={tile.numericValue} />
              )
            ) : (
              <span style={{ fontSize: tile.label === "LAST CRASH" ? 18 : 28 }}>
                {tile.rawValue}
              </span>
            )}
          </div>
        </motion.div>
      ))}
    </motion.div>
  );
}
