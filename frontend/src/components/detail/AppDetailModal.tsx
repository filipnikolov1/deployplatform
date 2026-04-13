"use client";

import useSWR from "swr";
import type { ComponentType } from "react";
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
  const titleId = `app-detail-${appName}`;

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
            <AppInfoRow app={app} />
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
              <Button leadingIcon={<Zap className="h-4 w-4" />}>Redeploy</Button>
              <Button variant="ghost-purple">View Logs</Button>
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
