"use client";

import { useState } from "react";
import { ChevronRight, GitBranch, Layers } from "lucide-react";
import { getDisplayImageName } from "@/lib/image-display";
import { formatRelative } from "@/lib/time";
import { statusConfig, toAppStatus } from "@/lib/statusConfig";
import type { Deployment } from "@/types/deployment";

interface AppTableProps {
  apps: Deployment[];
  onOpen: (appName: string) => void;
  title?: string;
}

function AppRow({ app, onOpen }: { app: Deployment; onOpen: (name: string) => void }) {
  const [hover, setHover] = useState(false);
  const displayImageName = getDisplayImageName(app);
  const status = toAppStatus(app.status);
  const config = statusConfig[status];
  const dotColor = config.dotColor;
  const chipFg   = config.fg;
  const chipBg   = config.bg;
  const chipBd   = config.line;

  return (
    <tr
      onClick={() => onOpen(app.appName)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      className="cursor-pointer transition-[background] duration-fast"
      style={{ background: hover ? "var(--c-surface-2)" : "transparent" }}
    >
      {/* Name + dot */}
      <td
        className="py-3 pl-3.5 pr-3.5"
        style={{ borderTop: "1px solid var(--c-border-1)" }}
      >
        <div className="flex items-center gap-2.5">
          <span
            className="h-2 w-2 rounded-full shrink-0"
            style={{ background: dotColor, boxShadow: `0 0 4px ${dotColor}` }}
          />
          <span
            className="text-sm font-medium whitespace-nowrap overflow-hidden text-ellipsis"
            style={{ color: "var(--c-fg-0)", maxWidth: 220 }}
          >
            {app.appName}
          </span>
        </div>
      </td>

      {/* Status chip */}
      <td
        className="py-3 px-3.5"
        style={{ borderTop: "1px solid var(--c-border-1)" }}
      >
        <span
          className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10.5px] font-medium whitespace-nowrap"
          style={{ color: chipFg, background: chipBg, border: `1px solid ${chipBd}` }}
        >
          <span
            className="h-1.5 w-1.5 rounded-full"
            style={{ background: dotColor, boxShadow: `0 0 4px ${dotColor}` }}
          />
          {config.label}
        </span>
      </td>

      {/* Image */}
      <td
        className="hidden lg:table-cell py-3 px-3.5 font-mono text-xs whitespace-nowrap overflow-hidden text-ellipsis"
        style={{
          borderTop: "1px solid var(--c-border-1)",
          color: "var(--c-fg-2)",
          maxWidth: 220,
        }}
      >
        {displayImageName}
      </td>

      {/* Branch */}
      <td
        className="hidden md:table-cell py-3 px-3.5"
        style={{ borderTop: "1px solid var(--c-border-1)" }}
      >
        {app.branch ? (
          <span
            className="inline-flex items-center gap-1.5 text-xs"
            style={{ color: "var(--c-fg-2)" }}
          >
            <GitBranch className="h-3 w-3" />
            {app.branch}
          </span>
        ) : (
          <span className="text-white/30">—</span>
        )}
      </td>

      {/* Port */}
      <td
        className="hidden md:table-cell py-3 px-3.5 font-mono text-xs tabular-nums"
        style={{ borderTop: "1px solid var(--c-border-1)", color: "var(--c-fg-2)" }}
      >
        :{app.containerPort}
      </td>

      {/* Updated */}
      <td
        className="hidden sm:table-cell py-3 px-3.5 text-xs whitespace-nowrap"
        style={{ borderTop: "1px solid var(--c-border-1)", color: "var(--c-fg-3)" }}
      >
        {formatRelative(app.updatedAt)}
      </td>

      {/* Chevron */}
      <td
        className="py-3 pr-2 pl-2 w-10"
        style={{
          borderTop: "1px solid var(--c-border-1)",
          color: "var(--c-fg-3)",
          opacity: hover ? 1 : 0,
          transition: "opacity 120ms",
        }}
      >
        <ChevronRight className="h-4 w-4" />
      </td>
    </tr>
  );
}

export function AppTable({ apps, onOpen, title = "All apps" }: AppTableProps) {
  return (
    <section>
      <div className="flex items-center gap-2.5 mb-2.5">
        <Layers className="h-3.5 w-3.5" style={{ color: "var(--c-fg-2)" }} />
        <h2
          className="text-[13px] font-semibold m-0 uppercase tracking-[0.14em]"
          style={{ color: "var(--c-fg-1)" }}
        >
          {title}
        </h2>
        <span className="text-xs" style={{ color: "var(--c-fg-3)" }}>{apps.length}</span>
      </div>
      <div
        className="overflow-hidden rounded-[14px]"
        style={{
          border: "1px solid var(--c-border-1)",
          background: "var(--c-surface-1)",
        }}
      >
        <table className="w-full border-collapse" style={{ tableLayout: "auto" }}>
          <thead>
            <tr style={{ background: "var(--c-surface-1)" }}>
              {["Name", "Status", "Image", "Branch", "Port", "Updated", ""].map((h) => (
                <th
                  key={h}
                  className="px-3.5 py-2.5 text-left text-[10.5px] font-semibold uppercase tracking-[0.12em]"
                  style={{ color: "var(--c-fg-3)" }}
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {apps.length === 0 ? (
              <tr>
                <td
                  colSpan={7}
                  className="py-10 text-center text-[13px]"
                  style={{ color: "var(--c-fg-3)" }}
                >
                  No apps match this filter.
                </td>
              </tr>
            ) : (
              apps.map((app) => (
                <AppRow key={app.appName} app={app} onOpen={onOpen} />
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
