"use client";

/**
 * Tabs — animated underline pill via motion.span layoutId.
 * The layoutId prop lets multiple Tabs instances coexist on a page without
 * the underline pill jumping between unrelated tab bars.
 */

import { motion } from "framer-motion";
import { M } from "@/design/tokens";

export interface TabItem {
  id: string;
  label: string;
}

interface TabsProps {
  tabs: TabItem[];
  active: string;
  onChange: (id: string) => void;
  /** Unique layoutId suffix — caller must supply so multiple Tabs can coexist */
  layoutId?: string;
}

export function Tabs({ tabs, active, onChange, layoutId = "tab-underline" }: TabsProps) {
  return (
    <div
      style={{
        display: "flex",
        gap: 4,
        borderBottom: `1px solid ${M.line}`,
      }}
      role="tablist"
    >
      {tabs.map((tab) => {
        const isActive = tab.id === active;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.id)}
            style={{
              position: "relative",
              padding: "10px 14px",
              background: "transparent",
              border: "none",
              cursor: "pointer",
              fontFamily: M.fontSans,
              fontSize: 13,
              fontWeight: 500,
              color: isActive ? M.fg : M.fg2,
              letterSpacing: "-0.005em",
              transition: "color 150ms",
              outline: "none",
            }}
          >
            {tab.label}
            {isActive && (
              <motion.span
                layoutId={layoutId}
                style={{
                  position: "absolute",
                  left: 10,
                  right: 10,
                  bottom: -1,
                  height: 1.5,
                  background: M.fg,
                  borderRadius: 1,
                }}
                transition={{ type: "spring", stiffness: 380, damping: 32 }}
              />
            )}
          </button>
        );
      })}
    </div>
  );
}
