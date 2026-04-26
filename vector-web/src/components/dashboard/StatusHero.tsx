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
  { key: "all",      label: "All apps",  icon: LayoutGrid,   color: "var(--c-fg-1)" },
  { key: "running",  label: "Running",   icon: CheckCircle2, color: "var(--c-status-running-fg)" },
  { key: "building", label: "Building",  icon: Activity,     color: "var(--c-status-building-fg)" },
  { key: "failed",   label: "Failed",    icon: XCircle,      color: "var(--c-status-failed-fg)" },
  { key: "stopped",  label: "Stopped",   icon: Clock,        color: "var(--c-status-stopped-fg)" },
] as const;

export function StatusHero({ activeFilter, onFilterChange, counts }: StatusHeroProps) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-2 mb-6">
      {ITEMS.map((item) => {
        const Icon = item.icon;
        const sel = item.key === activeFilter;
        return (
          <button
            key={item.key}
            onClick={() => onFilterChange(item.key)}
            aria-pressed={sel}
            className="flex flex-col gap-1.5 rounded-lg p-3.5 text-left transition-all duration-fast outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
            style={{
              background: sel ? "var(--c-surface-3)" : "var(--c-surface-1)",
              border: `1px solid ${sel ? "var(--c-accent-line)" : "var(--c-border-1)"}`,
            }}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs" style={{ color: "var(--c-fg-2)" }}>{item.label}</span>
              <Icon className="h-3.5 w-3.5" style={{ color: item.color }} />
            </div>
            <span
              className="text-2xl font-semibold tabular-nums leading-none"
              style={{ color: "var(--c-fg-0)" }}
            >
              {counts[item.key]}
            </span>
          </button>
        );
      })}
    </div>
  );
}
