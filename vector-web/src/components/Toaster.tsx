"use client";

/**
 * Toaster — mounts sonner's <Toaster> at the app root.
 * Style overrides match Vector Platform design tokens.
 */

import { Toaster as SonnerToaster } from "sonner";
import { M } from "@/design/tokens";

export function Toaster() {
  return (
    <SonnerToaster
      richColors
      expand={false}
      position="bottom-right"
      toastOptions={{
        style: {
          background: M.surface2,
          border: `1px solid ${M.line2}`,
          color: M.fg,
          fontFamily: M.fontSans,
          fontSize: 13,
          borderRadius: M.rLg,
        },
      }}
    />
  );
}
