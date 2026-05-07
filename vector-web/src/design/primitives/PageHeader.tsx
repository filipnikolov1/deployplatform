"use client";

/**
 * PageHeader — kicker + 32px title + subtitle + optional right-aligned actions slot.
 * 56px margin-bottom to next block (signature airiness of the design).
 */

import { motion } from "framer-motion";
import { M } from "@/design/tokens";

interface PageHeaderProps {
  kicker?: string;
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}

export function PageHeader({ kicker, title, subtitle, actions }: PageHeaderProps) {
  return (
    <header
      style={{
        display: "flex",
        alignItems: "flex-end",
        justifyContent: "space-between",
        gap: 24,
        padding: "44px 0 32px",
        marginBottom: 56,
        flexWrap: "wrap",
      }}
    >
      <div style={{ minWidth: 0, flex: 1 }}>
        {kicker && (
          <div
            style={{
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: "0.16em",
              textTransform: "uppercase",
              color: M.fg3,
              marginBottom: 8,
              fontFamily: M.fontSans,
            }}
          >
            {kicker}
          </div>
        )}
        <motion.h1
          initial={{ opacity: 0, y: 4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
          style={{
            fontFamily: M.fontSans,
            fontSize: 32,
            fontWeight: 600,
            color: M.fg,
            margin: 0,
            letterSpacing: "-0.025em",
            lineHeight: 1.1,
          }}
        >
          {title}
        </motion.h1>
        {subtitle && (
          <div
            style={{
              fontSize: 15,
              color: M.fg2,
              marginTop: 8,
              letterSpacing: "-0.005em",
              lineHeight: 1.5,
              maxWidth: 600,
              fontFamily: M.fontSans,
            }}
          >
            {subtitle}
          </div>
        )}
      </div>
      {actions && (
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          {actions}
        </div>
      )}
    </header>
  );
}
