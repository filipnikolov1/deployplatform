"use client";

import {
  Activity,
  CheckCircle2,
  Clock,
  LayoutGrid,
  XCircle,
} from "lucide-react";

export type HeroFilter = "all" | "running" | "building" | "failed" | "stopped";

interface StatusHeroProps {
  activeFilter: HeroFilter;
  onFilterChange: (filter: HeroFilter) => void;
  counts: Record<HeroFilter, number>;
}

const ITEMS = [
  { key: "all", label: "All apps", icon: LayoutGrid },
  { key: "running", label: "Running", icon: CheckCircle2 },
  { key: "building", label: "Building", icon: Activity },
  { key: "failed", label: "Failed", icon: XCircle },
  { key: "stopped", label: "Stopped", icon: Clock },
] as const;

export function StatusHero({
  activeFilter,
  onFilterChange,
  counts,
}: StatusHeroProps) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
      {ITEMS.map((item) => {
        const Icon = item.icon;
        return (
          <button
            key={item.key}
            onClick={() => onFilterChange(item.key)}
            aria-pressed={activeFilter === item.key}
            className={`relative flex flex-col gap-1 p-4 rounded-xl bg-white/[0.04] backdrop-blur-xl transition-all text-left focus-visible:ring-2 focus-visible:ring-accent-ghostLight outline-none ${
              activeFilter === item.key
                ? "border-2 border-accent-ghostLight"
                : "border border-white/[0.08] hover:border-white/[0.16]"
            }`}
          >
            <Icon className="absolute top-3 right-3 h-4 w-4 text-slate-400" />
            <span className="text-xs text-slate-400">{item.label}</span>
            <span className="text-2xl font-semibold text-white tabular-nums">
              {counts[item.key]}
            </span>
          </button>
        );
      })}
    </div>
  );
}
