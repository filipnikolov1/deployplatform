"use client";

import { GlassCard } from "@/components/primitives/GlassCard";
import { StatusDot } from "@/components/primitives/StatusDot";
import { formatRelative } from "@/lib/time";
import type { Deployment } from "@/types/deployment";

interface Props {
  app: Deployment;
  onOpen: (appName: string) => void;
}

export function AppCard({ app, onOpen }: Props) {
  const trigger = () => onOpen(app.appName);
  return (
    <GlassCard
      as="button"
      aria-label={`Open ${app.appName}`}
      radius="card"
      className="group flex w-full flex-col gap-3 p-4 text-left transition hover:bg-white/[0.06] focus:outline-none focus-visible:ring-focus"
      onClick={trigger}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          trigger();
        }
      }}
    >
      <div className="flex items-center gap-2">
        <StatusDot status={app.status} />
        <span className="text-base font-semibold text-slate-200">
          {app.appName}
        </span>
      </div>
      <div className="flex flex-col gap-1 font-mono text-xs text-slate-400 tabular-nums">
        <span className="truncate">{app.imageName}</span>
        <span>port {app.containerPort}</span>
        <span>updated {formatRelative(app.updatedAt)}</span>
      </div>
    </GlassCard>
  );
}
