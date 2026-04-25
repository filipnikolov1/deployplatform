"use client";

import { Sidebar } from "./Sidebar";
import { BottomNav } from "./BottomNav";
import { BackendUpdatingOverlay } from "./BackendUpdatingOverlay";
import { FrontendUpdatingOverlay } from "./FrontendUpdatingOverlay";
import { useUpdateAvailableNotifier } from "@/hooks/useUpdateAvailableNotifier";
import { useDeployProgressNotifier } from "@/hooks/useDeployProgressNotifier";

export function AppShell({ children }: { children: React.ReactNode }) {
  useUpdateAvailableNotifier();
  useDeployProgressNotifier();

  return (
    <div className="min-h-dvh">
      <Sidebar />
      {/* On md+: offset by sidebar width. On mobile: full width with bottom nav padding */}
      <main
        className="mx-auto max-w-7xl px-4 pb-28 pt-6 sm:px-8 sm:pb-6 md:pl-8"
        style={{
          paddingLeft: "calc(var(--sidebar-width) + 2rem)",
          paddingBottom: "calc(env(safe-area-inset-bottom) + 112px)",
        }}
      >
        {/* On small screens (no sidebar) reset to normal padding */}
        <style>{`
          @media (max-width: 767px) {
            main { padding-left: 1rem !important; }
          }
        `}</style>
        <div className="relative">
          <div className="pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent via-white/14 to-transparent" />
          {children}
        </div>
      </main>
      <BottomNav />
      <BackendUpdatingOverlay />
      <FrontendUpdatingOverlay />
    </div>
  );
}
