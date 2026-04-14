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
import { AppDetailSkeleton } from "./AppDetailSkeleton";
import { BuildLogViewer } from "./BuildLogViewer";
import { DeployHistoryList } from "./DeployHistoryList";
import { ActivityTimeline } from "@/components/activity/ActivityTimeline";
import { useEvents } from "@/hooks/useEvents";
import { useContainerStats } from "@/hooks/useContainerStats";
import { Clock, Cpu, HardDrive, RotateCcw, Zap } from "lucide-react";
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
  const toast = useToast();
  const { mutate } = useSWRConfig();
  const [redeploying, setRedeploying] = useState(false);
  const [downloadingLogs, setDownloadingLogs] = useState(false);
  const titleId = `app-detail-${appName}`;

  const handleRedeploy = async () => {
    if (redeploying || !app) return;
    setRedeploying(true);
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
        toast.error("Redeploy failed");
        return;
      }
      toast.success("Redeploy started");
      void mutate("/api/apps");
      void mutate(`/api/apps/${encodeURIComponent(app.appName)}`);
    } catch {
      toast.error("Redeploy failed");
    } finally {
      setRedeploying(false);
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
            <AppDetailHeader app={app} onClose={onClose} titleId={titleId} />
            <AppInfoRow
              app={app}
              publicHost={process.env.NEXT_PUBLIC_APP_BASE_DOMAIN ?? "localhost"}
            />
            <div className="grid grid-cols-2 gap-3 p-4">
              <InfoCard icon={Cpu} label="CPU" value={stats ? `${stats.cpuPercent.toFixed(1)}%` : "—"} />
              <InfoCard
                icon={HardDrive}
                label="Memory"
                value={
                  stats
                    ? `${Math.round(stats.memoryUsedMB)}MB / ${stats.memoryLimitMB}MB`
                    : "—"
                }
              />
              <InfoCard icon={Clock} label="Uptime" value={stats ? formatUptime(stats.uptimeSeconds) : "—"} />
              <InfoCard icon={RotateCcw} label="Restarts" value={stats ? String(stats.restartCount) : "—"} />
            </div>
            <div className="flex-1 overflow-hidden p-4">
              <Tabs
                tabs={[
                  {
                    id: "logs",
                    label: "Logs",
                    panel: (
                      <div className="min-h-[120px] max-h-[400px] overflow-auto font-mono text-xs bg-black/40 rounded-lg p-3">
                        <BuildLogViewer appName={app.appName} />
                      </div>
                    ),
                  },
                  {
                    id: "env",
                    label: "Env Vars",
                    panel: (
                      <div className="text-sm text-slate-400">
                        Env vars — implemented in Plan 3.
                      </div>
                    ),
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
            <div className="grid grid-cols-2 gap-3 p-6 border-t border-white/[0.08]">
              <Button
                leadingIcon={<Zap className="h-4 w-4" />}
                onClick={handleRedeploy}
                disabled={redeploying}
              >
                {redeploying ? "Redeploying…" : "Redeploy"}
              </Button>
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
    <div className="bg-white/[0.04] border border-white/[0.08] rounded-lg p-4">
      <div className="flex items-center gap-2 text-xs text-slate-400 mb-1">
        <Icon className="h-3.5 w-3.5" />
        {label}
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
