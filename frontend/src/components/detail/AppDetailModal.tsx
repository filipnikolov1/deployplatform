"use client";

import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import type { ComponentType } from "react";
import { useToast } from "@/hooks/useToast";
import { Modal } from "@/components/primitives/Modal";
import { GlassCard } from "@/components/primitives/GlassCard";
import { Tabs } from "@/components/primitives/Tabs";
import { AppDetailHeader } from "./AppDetailHeader";
import { AppInfoRow } from "./AppInfoRow";
import { EnvVarsTab } from "./EnvVarsTab";
import { AppDetailSkeleton } from "./AppDetailSkeleton";
import { BuildLogViewer } from "./BuildLogViewer";
import { DeployHistoryList } from "./DeployHistoryList";
import { ActivityTimeline } from "@/components/activity/ActivityTimeline";
import { useEvents } from "@/hooks/useEvents";
import { useContainerStats } from "@/hooks/useContainerStats";
import { useCommitsAhead } from "@/hooks/useCommitsAhead";
import {
  ArrowRight,
  ArrowUpCircle,
  Clock,
  Cpu,
  HardDrive,
  RotateCcw,
} from "lucide-react";
import { Button } from "@/components/primitives/Button";
import type { Deployment } from "@/types/deployment";

interface Props {
  appName: string;
  onClose: () => void;
}

const fetcher = async (url: string): Promise<Deployment> => {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export function AppDetailModal({ appName, onClose }: Props) {
  const { data: app } = useSWR<Deployment>(
    `/api/apps/${encodeURIComponent(appName)}`,
    fetcher,
    { refreshInterval: 10_000 },
  );
  const { events } = useEvents({ appName, limit: 20 });
  const { stats } = useContainerStats(appName);
  const { commitsAhead } = useCommitsAhead(appName);
  const toast = useToast();
  const { mutate } = useSWRConfig();
  const [restarting, setRestarting] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [downloadingLogs, setDownloadingLogs] = useState(false);
  const titleId = `app-detail-${appName}`;

  const mutateApp = () => {
    void mutate("/api/apps");
    void mutate(`/api/apps/${encodeURIComponent(appName)}`);
  };

  const handleRestart = async () => {
    if (restarting || !app) return;
    setRestarting(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(app.appName)}/restart`,
        { method: "POST" },
      );
      if (res.status === 409) {
        toast.error("App is busy — try again in a moment");
        return;
      }
      if (!res.ok) {
        toast.error("Restart failed");
        return;
      }
      toast.success("Restart started");
      mutateApp();
    } catch {
      toast.error("Restart failed");
    } finally {
      setRestarting(false);
    }
  };

  const handleStop = async () => {
    if (!app) return;
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(app.appName)}/stop`,
        { method: "POST" },
      );
      if (res.status === 409) {
        toast.error("App is busy — try again in a moment");
        return;
      }
      if (!res.ok) {
        toast.error("Stop failed");
        return;
      }
      toast.success("Container stopped");
      mutateApp();
    } catch {
      toast.error("Stop failed");
    }
  };

  const handleUpdate = async () => {
    if (updating || !app) return;
    setUpdating(true);
    try {
      const res = await fetch(
        `/api/self-apps/${encodeURIComponent(app.appName)}/update`,
        { method: "POST" },
      );
      if (!res.ok) {
        toast.error("Update failed");
        return;
      }
      toast.success("Update triggered — restarting");
      mutateApp();
      setTimeout(onClose, 1500);
    } catch {
      toast.error("Update failed");
    } finally {
      setUpdating(false);
    }
  };

  const handleDownloadLogs = async () => {
    if (downloadingLogs || !app) return;
    setDownloadingLogs(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(app.appName)}/logs/runtime/download`,
      );
      if (!res.ok) {
        toast.error("Log download failed");
        return;
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${app.appName}-runtime.log`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch {
      toast.error("Log download failed");
    } finally {
      setDownloadingLogs(false);
    }
  };

  const hasUpdate =
    app?.isSelfApp &&
    !!app.latestKnownSha &&
    app.commitSha !== app.latestKnownSha;

  return (
    <Modal open onClose={onClose} labelledBy={titleId}>
      <GlassCard
        radius="panel"
        className="sm:max-w-2xl sm:w-full bg-black/80 backdrop-blur-xl border border-white/[0.08] flex h-full w-full flex-col overflow-hidden sm:h-auto sm:max-h-[85dvh]"
      >
        {!app ? (
          <AppDetailSkeleton />
        ) : (
          <>
            <AppDetailHeader
              app={app}
              onClose={onClose}
              onRestart={handleRestart}
              onStop={handleStop}
              onUpdate={handleUpdate}
              restarting={restarting}
              updating={updating}
              titleId={titleId}
            />

            {/* Update available banner */}
            {hasUpdate && (
              <div
                role="status"
                aria-live="polite"
                className="mx-6 mt-4 flex items-center gap-4 rounded-2xl border border-amber-300/25 bg-amber-500/[0.08] p-4 backdrop-blur-xl"
              >
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-amber-300/30 bg-amber-500/15">
                  <ArrowUpCircle className="h-5 w-5 text-amber-200" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-semibold text-amber-100">
                    Update available
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-mono">
                    <span className="text-amber-100/60">running</span>
                    <span className="text-amber-100/90">
                      {app.commitSha?.slice(0, 7) ?? "—"}
                    </span>
                    <ArrowRight className="h-3 w-3 text-amber-100/50" />
                    <span className="text-amber-100/60">latest</span>
                    <span className="text-amber-100">
                      {app.latestKnownSha?.slice(0, 7)}
                    </span>
                  </div>
                  {app.latestKnownMessage && (
                    <div className="mt-1 truncate text-xs text-amber-100/70 font-sans">
                      {app.latestKnownMessage}
                    </div>
                  )}
                </div>
                <Button
                  variant="solid-amber"
                  onClick={handleUpdate}
                  disabled={updating}
                  loading={updating}
                  leadingIcon={<ArrowUpCircle className="h-4 w-4" />}
                >
                  {updating ? "Updating…" : "Update now"}
                </Button>
              </div>
            )}

            <AppInfoRow
              app={app}
              publicHost={
                process.env.NEXT_PUBLIC_APP_BASE_DOMAIN ?? "localhost"
              }
              commitsAhead={commitsAhead}
            />

            {/* Telemetry strip */}
            <div className="grid grid-cols-2 gap-3 p-4">
              <InfoCard
                icon={Cpu}
                label="CPU"
                value={stats ? `${stats.cpuPercent.toFixed(1)}%` : "—"}
              />
              <InfoCard
                icon={HardDrive}
                label="Memory"
                value={
                  stats
                    ? `${Math.round(stats.memoryUsedMB)}MB / ${stats.memoryLimitMB}MB`
                    : "—"
                }
              />
              <InfoCard
                icon={Clock}
                label="Uptime"
                value={stats ? formatUptime(stats.uptimeSeconds) : "—"}
              />
              <InfoCard
                icon={RotateCcw}
                label="Restarts"
                value={stats ? String(stats.restartCount) : "—"}
              />
            </div>

            {/* Tabs */}
            <div className="flex-1 min-h-0 overflow-hidden p-4">
              <Tabs
                tabs={[
                  {
                    id: "logs",
                    label: "Logs",
                    panel: (
                      <div className="h-full min-h-[120px] min-h-0 font-mono text-xs bg-black/40 rounded-lg p-3">
                        <BuildLogViewer appName={app.appName} />
                      </div>
                    ),
                  },
                  {
                    id: "env",
                    label: "Env Vars",
                    panel: <EnvVarsTab appName={app.appName} />,
                  },
                  {
                    id: "activity",
                    label: "Activity",
                    panel:
                      events.length === 0 ? (
                        <div className="text-sm text-slate-400">
                          No recent activity.
                        </div>
                      ) : (
                        <div className="max-h-[400px] overflow-auto">
                          <ActivityTimeline events={events} />
                        </div>
                      ),
                  },
                  {
                    id: "history",
                    label: "History",
                    panel: (
                      <div className="max-h-[400px] overflow-auto">
                        <DeployHistoryList
                          appName={app.appName}
                          events={events}
                        />
                      </div>
                    ),
                  },
                ]}
              />
            </div>

            {/* Footer — Download Logs only */}
            <div className="flex justify-end p-6 border-t border-white/[0.08]">
              <Button
                variant="ghost-purple"
                onClick={handleDownloadLogs}
                disabled={downloadingLogs}
              >
                {downloadingLogs ? "Preparing…" : "Download Logs"}
              </Button>
            </div>
          </>
        )}
      </GlassCard>
    </Modal>
  );
}

function InfoCard({
  icon: Icon,
  label,
  value,
}: {
  icon: ComponentType<{ className?: string }>;
  label: string;
  value: string;
}) {
  return (
    <div className="bg-white/[0.04] border border-white/[0.08] rounded-lg p-3">
      <div className="flex items-center gap-2 mb-1">
        <Icon className="h-3.5 w-3.5 text-slate-400" />
        <span className="text-[10px] uppercase tracking-[0.14em] text-slate-400">
          {label}
        </span>
      </div>
      <div className="text-sm text-slate-100 tabular-nums">{value}</div>
    </div>
  );
}

function formatUptime(uptimeSeconds: number): string {
  const days = Math.floor(uptimeSeconds / 86_400);
  const hours = Math.floor((uptimeSeconds % 86_400) / 3_600);
  const mins = Math.floor((uptimeSeconds % 3_600) / 60);
  const secs = uptimeSeconds % 60;
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${mins}m`;
  return `${mins}m ${secs}s`;
}
