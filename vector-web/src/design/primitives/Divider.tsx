"use client";

/**
 * Divider — hairline horizontal rule at M.line (0.06 opacity).
 */

import { M } from "@/design/tokens";

interface DividerProps {
  /** Vertical margin on each side in px */
  space?: number;
}

export function Divider({ space = 24 }: DividerProps) {
  return (
    <div
      role="separator"
      aria-hidden="true"
      style={{
        height: 1,
        background: M.line,
        margin: `${space}px 0`,
      }}
    />
  );
}
