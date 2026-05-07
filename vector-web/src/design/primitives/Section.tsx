"use client";

/**
 * Section — uppercase kicker title + optional count chip + hint text + optional action slot.
 * Children rendered below header row.
 */

import { M } from "@/design/tokens";

interface SectionProps {
  title: string;
  count?: number | null;
  hint?: string;
  action?: React.ReactNode;
  children?: React.ReactNode;
}

export function Section({ title, count, hint, action, children }: SectionProps) {
  return (
    <section style={{ marginBottom: 56 }}>
      <div
        style={{
          display: "flex",
          alignItems: "baseline",
          justifyContent: "space-between",
          marginBottom: 16,
          gap: 16,
        }}
      >
        <div style={{ display: "flex", alignItems: "baseline", gap: 12 }}>
          <h2
            style={{
              fontFamily: M.fontSans,
              fontSize: 13,
              fontWeight: 600,
              color: M.fg,
              margin: 0,
              letterSpacing: "0.04em",
              textTransform: "uppercase",
            }}
          >
            {title}
          </h2>
          {count != null && (
            <span
              style={{
                fontSize: 12,
                color: M.fg3,
                fontVariantNumeric: "tabular-nums",
                fontFamily: M.fontSans,
              }}
            >
              {count}
            </span>
          )}
          {hint && (
            <span
              style={{
                fontSize: 12,
                color: M.fg3,
                letterSpacing: "-0.005em",
                fontFamily: M.fontSans,
              }}
            >
              {hint}
            </span>
          )}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}
