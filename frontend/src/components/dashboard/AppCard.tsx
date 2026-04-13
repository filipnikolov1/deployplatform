"use client";

import { useState } from "react";
import type { KeyboardEvent, MouseEvent } from "react";
import { motion } from "framer-motion";
import { Clock, GitBranch, MoreVertical } from "lucide-react";
import { formatRelative } from "@/lib/time";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";
import { useCommitsAhead } from "@/hooks/useCommitsAhead";
import { statusConfig, toAppStatus } from "@/lib/statusConfig";
import type { Deployment } from "@/types/deployment";

interface Props {
  app: Deployment;
  onOpen: (appName: string) => void;
}

export function AppCard({ app, onOpen }: Props) {
  const prefersReducedMotion = usePrefersReducedMotion();
  const { commitsAhead } = useCommitsAhead(app.appName);
  const [menuOpen, setMenuOpen] = useState(false);
  const status = toAppStatus(app.status);
  const config = statusConfig[status];
  const Icon = config.icon;
  const trigger = () => onOpen(app.appName);

  return (
    <motion.article
      layout
      whileHover={prefersReducedMotion ? undefined : { y: -4 }}
      transition={{ type: "spring", stiffness: 300, damping: 30 }}
      role="button"
      tabIndex={0}
      aria-label={`Open ${app.appName}`}
      className="group relative bg-white/[0.04] border border-white/[0.12] rounded-xl p-5 backdrop-blur-xl cursor-pointer hover:bg-white/[0.06] transition-colors focus-visible:ring-2 focus-visible:ring-accent-ghostLight outline-none"
      onClick={trigger}
      onKeyDown={(e: KeyboardEvent<HTMLElement>) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          trigger();
        }
      }}
    >
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setMenuOpen((prev) => !prev);
        }}
        className="absolute top-3 right-3 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity h-11 w-11 md:h-8 md:w-8 rounded-lg hover:bg-white/10 flex items-center justify-center"
      >
        <MoreVertical className="h-4 w-4 text-slate-300" />
      </button>
      {menuOpen && (
        <div className="absolute top-12 right-3 z-10 rounded-lg border border-white/[0.12] bg-black/90 p-1.5 text-sm">
          {["Redeploy", "Stop", "Pin", "Delete"].map((item) => (
            <button
              key={item}
              type="button"
              onClick={(e: MouseEvent<HTMLButtonElement>) => e.stopPropagation()}
              className="block w-full rounded px-3 py-1.5 text-left text-slate-200 hover:bg-white/10"
            >
              {item}
            </button>
          ))}
        </div>
      )}

      <div className="flex items-start gap-3">
        <div className={`p-2 rounded-lg ${config.bgColor}`}>
          <Icon className={`h-4 w-4 ${config.iconColor}`} />
        </div>
        <div className="min-w-0">
          <p className="text-base font-semibold text-white truncate">{app.appName}</p>
          <div className="flex items-center gap-1.5 text-xs text-slate-400 mt-0.5">
            <GitBranch className="h-3 w-3" />
            <span>main</span>
          </div>
        </div>
      </div>

      <div
        className={`inline-flex w-fit items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${config.bgColor} ${config.borderColor} ${config.iconColor}`}
      >
        <Icon className="h-3 w-3" />
        {config.label}
      </div>

      <div className="flex items-center justify-between gap-2 pt-3 pb-3 border-t border-white/[0.08] mt-3">
        <span className="text-xs text-slate-400 truncate font-mono">{app.imageName}</span>
      </div>

      <div className="grid grid-cols-3 gap-4 border-t border-white/[0.08] pt-3 text-xs">
        <div>
          <span className="text-slate-400 block mb-0.5">Framework</span>
          <span className="text-slate-300 font-medium truncate">Docker</span>
        </div>
        <div>
          <span className="text-slate-400 block mb-0.5">Branch</span>
          <span className="text-slate-300 font-medium truncate">main</span>
        </div>
        <div>
          <span className="text-slate-400 block mb-0.5">Port</span>
          <span className="text-slate-300 font-medium truncate tabular-nums">{app.containerPort}</span>
        </div>
      </div>

      <div className="flex items-center justify-between mt-3 text-xs text-slate-400">
        <span className="inline-flex items-center gap-1">
          <Clock className="h-3 w-3" />
          Deployed {formatRelative(app.updatedAt)}
        </span>
        {commitsAhead.count && commitsAhead.count > 0 && (
          <a
            href={commitsAhead.compareUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-300 text-xs font-medium border border-purple-500/20 hover:bg-purple-500/20 transition-colors"
            onClick={(e: MouseEvent<HTMLAnchorElement>) => e.stopPropagation()}
            aria-label={`${commitsAhead.count} commits ahead on main`}
          >
            <GitBranch className="h-3 w-3" />
            {commitsAhead.count} ahead
          </a>
        )}
      </div>
    </motion.article>
  );
}
