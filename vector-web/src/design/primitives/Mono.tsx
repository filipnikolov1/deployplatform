"use client";

/**
 * Mono — inline monospace span for SHAs, image tags, ports, log tokens.
 */

import { CSSProperties } from "react";
import { M } from "@/design/tokens";

interface MonoProps {
  children: React.ReactNode;
  style?: CSSProperties;
  className?: string;
}

export function Mono({ children, style, className }: MonoProps) {
  return (
    <span
      style={{
        fontFamily: M.fontMono,
        fontSize: 12,
        color: M.fg2,
        letterSpacing: "0.04em",
        ...style,
      }}
      className={className}
    >
      {children}
    </span>
  );
}
