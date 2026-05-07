"use client";

/**
 * AppDetailDrawer — F5.1
 * Spring-slide from right. Tab structure: Overview | Logs | Env Vars | Timeline | Settings.
 * AnimatePresence mode="wait" crossfades tab content.
 */

import { useState, useEffect } from "react";
import { createPortal } from "react-dom";
import useSWR from "swr";
import { motion, AnimatePresence } from "framer-motion";
import { M, MMOTION, type MStatus } from "@/design/tokens";
import { Tabs } from "@/design/primitives/Tabs";
import { DrawerHeader } from "./DrawerHeader";
import { OverviewTab } from "./tabs/OverviewTab";
import { LogsTab } from "./tabs/LogsTab";
import { EnvVarsTab } from "./tabs/EnvVarsTab";
import { TimelineTab } from "./tabs/TimelineTab";
import { SettingsTab } from "./tabs/SettingsTab";
import { useEvents } from "@/hooks/useEvents";
import { toAppStatus } from "@/lib/statusConfig";
import type { Deployment } from "@/types/deployment";
import type { TabItem } from "@/design/primitives/Tabs";

interface Props {
  appName: string;
  onClose: () => void;
}

const fetcher = async (url: string): Promise<Deployment> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

const TABS: TabItem[] = [
  { id: "overview", label: "Overview" },
  { id: "logs", label: "Logs" },
  { id: "env", label: "Env Vars" },
  { id: "timeline", label: "Timeline" },
  { id: "settings", label: "Settings" },
];

type TabId = "overview" | "logs" | "env" | "timeline" | "settings";

// Skeleton shown while app data loads
function DrawerSkeleton() {
  const shimmer: React.CSSProperties = {
    background: M.surface2,
    borderRadius: M.rSm,
    animation: "lp-pulse 1.5s ease-in-out infinite",
  };
  return (
    <div style={{ padding: "20px 24px", display: "flex", flexDirection: "column", gap: 14 }}>
      <div style={{ ...shimmer, height: 14, width: 60 }} />
      <div style={{ ...shimmer, height: 22, width: 180 }} />
      <div style={{ ...shimmer, height: 1, width: "100%", marginTop: 8 }} />
      {Array.from({ length: 5 }).map((_, i) => (
        <div
          key={i}
          style={{ ...shimmer, height: 12, width: `${60 + (i * 7) % 30}%` }}
        />
      ))}
    </div>
  );
}

export function AppDetailDrawer({ appName, onClose }: Props) {
  const [tab, setTab] = useState<TabId>("overview");
  const titleId = `drawer-${appName}`;

  const { data: app } = useSWR<Deployment>(
    `/api/apps/${encodeURIComponent(appName)}`,
    fetcher,
    { refreshInterval: 10_000 }
  );

  const { events } = useEvents({ appName, limit: 50 });

  // Keyboard + scroll-lock
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      document.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  if (typeof document === "undefined") return null;

  const status: MStatus = app ? (() => {
    const s = toAppStatus(app.status);
    // Map statusConfig AppStatus → MStatus key
    const MAP: Record<string, MStatus> = {
      RUNNING: "RUNNING",
      BUILDING: "BUILDING",
      FAILED: "FAILED",
      CRASHED: "CRASHED",
      STOPPED: "STOPPED",
      PENDING: "PENDING",
    };
    return MAP[s] ?? "STOPPED";
  })() : "STOPPED";

  return createPortal(
    <AnimatePresence>
      {/* Backdrop */}
      <motion.div
        key="drawer-backdrop"
        {...MMOTION.backdrop}
        style={{
          position: "fixed",
          inset: 0,
          zIndex: 100,
          background: "rgba(0,0,0,0.48)",
          backdropFilter: "blur(2px)",
        }}
        onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
        role="presentation"
      >
        {/* Drawer panel */}
        <motion.aside
          key="drawer-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          initial={MMOTION.drawer.initial}
          animate={MMOTION.drawer.animate}
          exit={MMOTION.drawer.exit}
          style={{
            position: "absolute",
            top: 0,
            right: 0,
            bottom: 0,
            width: "min(660px, 100vw)",
            background: M.bg,
            borderLeft: `1px solid ${M.line}`,
            display: "flex",
            flexDirection: "column",
            boxShadow: "0 0 80px rgba(0,0,0,0.6)",
            overflow: "hidden",
          }}
        >
          {!app ? (
            <DrawerSkeleton />
          ) : (
            <>
              {/* Header */}
              <DrawerHeader app={app} status={status} onClose={onClose} />

              {/* Tabs row */}
              <div style={{ paddingLeft: 24, paddingRight: 24, paddingTop: 4, flexShrink: 0 }}>
                <Tabs
                  tabs={TABS}
                  active={tab}
                  onChange={(id) => setTab(id as TabId)}
                  layoutId="drawer-tabs"
                />
              </div>

              {/* Tab content */}
              <div
                style={{
                  flex: 1,
                  minHeight: 0,
                  overflow: tab === "logs" ? "hidden" : "auto",
                  position: "relative",
                }}
              >
                <AnimatePresence mode="wait">
                  <motion.div
                    key={tab}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -4 }}
                    transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
                    style={{
                      padding: "16px 24px 24px",
                      height: tab === "logs" ? "100%" : undefined,
                      display: tab === "logs" ? "flex" : undefined,
                      flexDirection: tab === "logs" ? "column" : undefined,
                    }}
                  >
                    {tab === "overview" && (
                      <OverviewTab app={app} events={events} />
                    )}
                    {tab === "logs" && (
                      <LogsTab appName={app.appName} />
                    )}
                    {tab === "env" && (
                      <EnvVarsTab appName={app.appName} />
                    )}
                    {tab === "timeline" && (
                      <TimelineTab
                        appName={app.appName}
                        events={events}
                        currentImage={app.imageName}
                      />
                    )}
                    {tab === "settings" && (
                      <SettingsTab app={app} onClose={onClose} />
                    )}
                  </motion.div>
                </AnimatePresence>
              </div>
            </>
          )}
        </motion.aside>
      </motion.div>
    </AnimatePresence>,
    document.body
  );
}
