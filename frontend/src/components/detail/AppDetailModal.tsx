"use client";

import useSWR from "swr";
import { Modal } from "@/components/primitives/Modal";
import { GlassCard } from "@/components/primitives/GlassCard";
import { Tabs } from "@/components/primitives/Tabs";
import { AppDetailHeader } from "./AppDetailHeader";
import { AppInfoRow } from "./AppInfoRow";
import { AppDetailSkeleton } from "./AppDetailSkeleton";
import { BuildLogViewer } from "./BuildLogViewer";
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
  const titleId = `app-detail-${appName}`;

  return (
    <Modal open onClose={onClose} labelledBy={titleId}>
      <GlassCard
        radius="panel"
        className="flex h-full w-full flex-col overflow-hidden sm:h-auto sm:max-h-[85dvh]"
      >
        {!app ? (
          <AppDetailSkeleton />
        ) : (
          <>
            <AppDetailHeader app={app} onClose={onClose} titleId={titleId} />
            <AppInfoRow app={app} />
            <div className="flex-1 overflow-hidden p-4">
              <Tabs
                tabs={[
                  {
                    id: "logs",
                    label: "Logs",
                    panel: <BuildLogViewer appName={app.appName} />,
                  },
                  {
                    id: "env",
                    label: "Env Vars",
                    panel: (
                      <div className="text-sm text-slate-500">
                        Env vars — implemented in Plan 3.
                      </div>
                    ),
                  },
                ]}
              />
            </div>
          </>
        )}
      </GlassCard>
    </Modal>
  );
}
