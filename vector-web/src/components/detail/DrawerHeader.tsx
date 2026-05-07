"use client";

/**
 * DrawerHeader — F5.1
 * Kicker (SELF-APP / APP) → name (32px) + StatusChip inline.
 * Right side: Time Machine link + close X.
 * No status icon — conveyed by the chip alone.
 */

import Link from "next/link";
import { X, History } from "lucide-react";
import { StatusChip } from "@/design/primitives/StatusChip";
import { M, MSTATUS, type MStatus } from "@/design/tokens";
import type { Deployment } from "@/types/deployment";

interface DrawerHeaderProps {
  app: Deployment;
  status: MStatus;
  onClose: () => void;
}

export function DrawerHeader({ app, status, onClose }: DrawerHeaderProps) {
  const kicker = app.isSelfApp ? "SELF-APP" : "APP";

  return (
    <header
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 6,
        padding: "20px 24px 16px",
        borderBottom: `1px solid ${M.line}`,
      }}
    >
      {/* Top row: kicker + close */}
      <div
        style={{
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: 12,
        }}
      >
        <span
          style={{
            fontFamily: M.fontMono,
            fontSize: 10,
            fontWeight: 600,
            letterSpacing: "0.14em",
            textTransform: "uppercase",
            color: app.isSelfApp ? M.accent : M.fg3,
          }}
        >
          {kicker}
        </span>

        {/* Right: Time Machine + close */}
        <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
          <Link
            href={`/apps/${encodeURIComponent(app.appName)}/time-machine`}
            onClick={() => localStorage.setItem("lastTimeMachineApp", app.appName)}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 6,
              padding: "6px 12px",
              borderRadius: M.rPill,
              fontSize: 12.5,
              fontWeight: 500,
              fontFamily: M.fontSans,
              color: M.fg2,
              background: "transparent",
              border: `1px solid ${M.line2}`,
              textDecoration: "none",
              cursor: "pointer",
              letterSpacing: "-0.005em",
              whiteSpace: "nowrap",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = M.fg;
              e.currentTarget.style.background = M.surface2;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = M.fg2;
              e.currentTarget.style.background = "transparent";
            }}
          >
            <History style={{ width: 13, height: 13 }} />
            Time Machine
          </Link>

          <button
            type="button"
            aria-label="Close drawer"
            onClick={onClose}
            style={{
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              width: 30,
              height: 30,
              borderRadius: M.rMd,
              background: M.surface2,
              border: `1px solid ${M.line}`,
              color: M.fg3,
              cursor: "pointer",
              flexShrink: 0,
              outline: "none",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = M.fg;
              e.currentTarget.style.borderColor = M.line2;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = M.fg3;
              e.currentTarget.style.borderColor = M.line;
            }}
          >
            <X style={{ width: 14, height: 14 }} />
          </button>
        </div>
      </div>

      {/* Name + chip row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 10,
          flexWrap: "wrap",
          minWidth: 0,
        }}
      >
        <h2
          style={{
            margin: 0,
            fontSize: 24,
            fontWeight: 600,
            fontFamily: M.fontSans,
            letterSpacing: "-0.02em",
            color: M.fg,
            lineHeight: 1.2,
            minWidth: 0,
            flex: "0 1 auto",
            wordBreak: "break-all",
          }}
        >
          {app.appName}
        </h2>
        <StatusChip status={status} compact />
      </div>
    </header>
  );
}
