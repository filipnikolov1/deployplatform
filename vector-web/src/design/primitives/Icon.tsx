"use client";

/**
 * Icon — Lucide icon wrapper.
 * Uses lucide-react (bundler-resolved) via dynamic key lookup.
 * Public API: <Icon name="git-branch" size={11} color={M.fg2} />
 *
 * useLucide() is a hook callers can invoke at the top of a page-level component
 * if they mix this Icon primitive with any raw <i data-lucide> elements from the
 * prototype HTML. In a real bundler tree it's a no-op — the icons resolve at
 * import time, not via lucide.createIcons().
 */

import { useEffect, CSSProperties } from "react";
import * as LucideIcons from "lucide-react";
import { M } from "@/design/tokens";

type LucideIconName = string;

interface IconProps {
  name: LucideIconName;
  size?: number;
  color?: string;
  style?: CSSProperties;
  className?: string;
  strokeWidth?: number;
}

/** Convert kebab-case icon name to PascalCase component name. */
function toPascalCase(name: string): string {
  return name
    .split("-")
    .map((seg) => seg.charAt(0).toUpperCase() + seg.slice(1))
    .join("");
}

export function Icon({
  name,
  size = 14,
  color,
  style,
  className,
  strokeWidth = 1.5,
}: IconProps) {
  const pascalName = toPascalCase(name);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const LucideComponent = (LucideIcons as Record<string, any>)[pascalName] as
    | React.FC<React.SVGProps<SVGSVGElement> & { size?: number; strokeWidth?: number }>
    | undefined;

  if (!LucideComponent) {
    // Graceful fallback: render a small square placeholder so layout doesn't break
    return (
      <span
        style={{
          display: "inline-block",
          width: size,
          height: size,
          background: M.fg4,
          borderRadius: 2,
          flexShrink: 0,
          ...style,
        }}
        className={className}
        aria-hidden="true"
      />
    );
  }

  return (
    <LucideComponent
      size={size}
      strokeWidth={strokeWidth}
      color={color ?? "currentColor"}
      style={{ flexShrink: 0, display: "inline-flex", ...style }}
      className={className}
      aria-hidden="true"
    />
  );
}

/**
 * useLucide — compatibility hook for prototype-style <i data-lucide="..."> elements.
 * In production builds with lucide-react, this is a no-op because icons resolve at
 * bundle time. Kept for parity with the MPrimitives.jsx handoff API.
 */
export function useLucide() {
  useEffect(() => {
    // In a bundler environment lucide-react SVGs are injected at render time.
    // This hook exists for API parity with the UMD-based handoff prototype only.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    if (typeof window !== "undefined" && (window as any).lucide) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).lucide.createIcons();
    }
  });
}
