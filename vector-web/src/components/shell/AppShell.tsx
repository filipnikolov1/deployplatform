"use client";

/**
 * AppShell — root layout shell.
 *
 * - Mounts <CommandPalette /> (Toaster already in app/layout.tsx — NOT mounted again).
 * - Binds global Cmd+K / Ctrl+K keydown to commandPaletteStore.
 * - Renders <Sidebar /> + main column (margin-left: 220px, max-width: 1280px, padding: 0 56px 80px).
 */

import { useEffect } from "react";
import type { ReactNode } from "react";

import { Sidebar, SIDEBAR_WIDTH } from "./Sidebar";
import { BottomNav } from "./BottomNav";
import { CommandPalette } from "@/components/command-palette";
import { commandPaletteStore } from "@/hooks/useCommandPalette";
import { useUpdateAvailableNotifier } from "@/hooks/useUpdateAvailableNotifier";
import { useDeployProgressNotifier } from "@/hooks/useDeployProgressNotifier";
import { EventStreamProvider } from "@/hooks/useEventStream";

// ── Global Cmd+K / Ctrl+K binding ────────────────────────────────────────────

function useGlobalCmdK() {
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const isMac = typeof navigator !== "undefined" && /Mac/i.test(navigator.platform);
      const trigger = isMac
        ? e.metaKey && e.key === "k"
        : e.ctrlKey && e.key === "k";
      if (trigger) {
        e.preventDefault();
        commandPaletteStore.toggle();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);
}

// ── AppShellInner (uses hooks — must be inside EventStreamProvider) ───────────

function AppShellInner({ children }: { children: ReactNode }) {
  useUpdateAvailableNotifier();
  useDeployProgressNotifier();
  useGlobalCmdK();

  return (
    <div style={{ minHeight: "100dvh" }}>
      {/* Fixed left sidebar */}
      <Sidebar />

      {/* Main content column */}
      <main
        style={{
          marginLeft: SIDEBAR_WIDTH,
          maxWidth: `calc(1280px + ${SIDEBAR_WIDTH}px)`,
          paddingLeft: 56,
          paddingRight: 56,
          paddingBottom: 80,
          paddingTop: 0,
          boxSizing: "border-box",
        }}
      >
        <style>{`
          @media (max-width: 767px) {
            main {
              margin-left: 0 !important;
              padding-left: 1rem !important;
              padding-right: 1rem !important;
            }
          }
        `}</style>
        {children}
      </main>

      {/* Mobile bottom nav */}
      <BottomNav />

      {/* Global Cmd+K palette — Toaster is in app/layout.tsx, not here */}
      <CommandPalette />
    </div>
  );
}

// ── AppShell (public) ─────────────────────────────────────────────────────────

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <EventStreamProvider>
      <AppShellInner>{children}</AppShellInner>
    </EventStreamProvider>
  );
}
