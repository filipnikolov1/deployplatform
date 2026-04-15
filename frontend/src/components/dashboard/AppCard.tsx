"use client";

import { useState } from "react";
import type { KeyboardEvent, MouseEvent } from "react";
import { useSWRConfig } from "swr";
import { motion } from "framer-motion";
import { Clock, GitBranch, MoreVertical } from "lucide-react";
import { formatRelative } from "@/lib/time";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";
import { useCommitsAhead } from "@/hooks/useCommitsAhead";
import { usePreferences } from "@/hooks/usePreferences";
import { useToast } from "@/hooks/useToast";
import { statusConfig, toAppStatus } from "@/lib/statusConfig";
import type { Deployment } from "@/types/deployment";

interface Props {
  app: Deployment;
  onOpen: (appName: string) => void;
  mode?: "grid" | "list";
}

export function AppCard({ app, onOpen, mode = "grid" }: Props) {
  const prefersReducedMotion = usePrefersReducedMotion();
  const { commitsAhead } = useCommitsAhead(app.appName);
  const { prefs } = usePreferences();
  const toast = useToast();
  const { mutate } = useSWRConfig();
  const [menuOpen, setMenuOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const status = toAppStatus(app.status);
  const config = statusConfig[status];
  const Icon = config.icon;
  const trigger = () => onOpen(app.appName);
  const isPinned = prefs.pinned_apps.includes(app.appName);
  const branchLabel = app.branch ?? "main";

  const closeMenu = () => setMenuOpen(false);

  const refreshApps = () => {
    void mutate("/api/apps");
    void mutate(`/api/apps/${encodeURIComponent(app.appName)}`);
  };

  const handleAction = async (
    e: MouseEvent<HTMLButtonElement>,
    action: "redeploy" | "stop" | "pin" | "delete",
  ) => {
    e.stopPropagation();
    closeMenu();
    if (busy) return;
    setBusy(true);
    try {
      if (action === "redeploy") {
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
        refreshApps();
      } else if (action === "stop") {
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
        toast.success("App stopped");
        refreshApps();
      } else if (action === "pin") {
        const method = isPinned ? "DELETE" : "POST";
        const res = await fetch(
          `/api/me/pin/${encodeURIComponent(app.appName)}`,
          { method },
        );
        if (!res.ok) {
          toast.error(isPinned ? "Unpin failed" : "Pin failed");
          return;
        }
        toast.success(isPinned ? "Unpinned" : "Pinned");
        void mutate("/api/me");
      } else if (action === "delete") {
        const res = await fetch(
          `/api/apps/${encodeURIComponent(app.appName)}`,
          { method: "DELETE" },
        );
        if (!res.ok) {
          toast.error("Delete failed");
          return;
        }
        toast.success(`${app.appName} deleted`, {
          durationMs: 10_000,
          action: {
            label: "Undo",
            onClick: async () => {
              try {
                const restore = await fetch(
                  `/api/apps/${encodeURIComponent(app.appName)}/restore`,
                  { method: "POST" },
                );
                if (!restore.ok) {
                  toast.error("Undo failed");
                  return;
                }
                toast.success("Restored");
                refreshApps();
              } catch {
                toast.error("Undo failed");
              }
            },
          },
        });
        refreshApps();
      }
    } catch {
      toast.error("Request failed");
    } finally {
      setBusy(false);
    }
  };

  const handleSelfUpdate = async (e: MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    if (busy) return;
    setBusy(true);
    try {
      const res = await fetch(
        `/api/self-apps/${encodeURIComponent(app.appName)}/update`,
        { method: "POST" },
      );
      if (!res.ok) {
        toast.error("Update failed");
        return;
      }
      toast.success("Update started");
      refreshApps();
    } catch {
      toast.error("Update failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <motion.article
      layout
      whileHover={prefersReducedMotion ? undefined : { y: -4 }}
      transition={{ type: "spring", stiffness: 300, damping: 30 }}
      role="button"
      tabIndex={0}
      aria-label={`Open ${app.appName}`}
      className={`group relative bg-white/[0.04] border border-white/[0.12] rounded-xl backdrop-blur-xl cursor-pointer hover:bg-white/[0.06] transition-colors focus-visible:ring-2 focus-visible:ring-accent-ghostLight outline-none ${
        mode === "list" ? "p-4" : "p-5"
      }`}
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
        <div className="absolute top-12 right-3 z-10 rounded-lg border border-white/[0.12] bg-black/90 p-1.5 text-sm min-w-[140px]">
          <button
            type="button"
            disabled={busy}
            onClick={(e) => handleAction(e, "redeploy")}
            className="block w-full rounded px-3 py-1.5 text-left text-slate-200 hover:bg-white/10 disabled:opacity-60"
          >
            Redeploy
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={(e) => handleAction(e, "stop")}
            className="block w-full rounded px-3 py-1.5 text-left text-slate-200 hover:bg-white/10 disabled:opacity-60"
          >
            Stop
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={(e) => handleAction(e, "pin")}
            className="block w-full rounded px-3 py-1.5 text-left text-slate-200 hover:bg-white/10 disabled:opacity-60"
          >
            {isPinned ? "Unpin" : "Pin"}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={(e) => handleAction(e, "delete")}
            className="block w-full rounded px-3 py-1.5 text-left text-red-300 hover:bg-red-500/10 disabled:opacity-60"
          >
            Delete
          </button>
        </div>
      )}

      <div className={`flex items-start gap-3 ${mode === "list" ? "pr-10" : ""}`}>
        <div className={`p-2 rounded-lg ${config.bgColor}`}>
          <Icon className={`h-4 w-4 ${config.iconColor}`} />
        </div>
        <div className="min-w-0">
          <p className="text-base font-semibold text-white truncate">{app.appName}</p>
          <div className="flex items-center gap-1.5 text-xs text-slate-400 mt-0.5">
            <GitBranch className="h-3 w-3" />
            <span>{branchLabel}</span>
          </div>
        </div>
      </div>

      {mode === "list" ? (
        <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-white/[0.08] pt-3 text-xs">
          <span
            className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full font-medium border ${config.bgColor} ${config.borderColor} ${config.iconColor}`}
          >
            <Icon className="h-3 w-3" />
            {config.label}
          </span>
          <span className="text-slate-400 font-mono truncate">{app.imageName}</span>
          <span className="text-slate-300 tabular-nums">port {app.containerPort}</span>
          <span className="text-slate-400 inline-flex items-center gap-1">
            <Clock className="h-3 w-3" />
            {formatRelative(app.updatedAt)}
          </span>
          {commitsAhead.count && commitsAhead.count > 0 && (
            <a
              href={commitsAhead.compareUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-300 text-xs font-medium border border-purple-500/20 hover:bg-purple-500/20 transition-colors"
              onClick={(e: MouseEvent<HTMLAnchorElement>) => e.stopPropagation()}
              aria-label={`${commitsAhead.count} commits ahead on ${branchLabel}`}
            >
              <GitBranch className="h-3 w-3" />
              {commitsAhead.count} ahead
            </a>
          )}
        </div>
      ) : (
        <>
          <div
            className={`inline-flex w-fit items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${config.bgColor} ${config.borderColor} ${config.iconColor}`}
          >
            <Icon className="h-3 w-3" />
            {config.label}
          </div>

          <div className="flex items-center justify-between gap-2 pt-3 pb-3 border-t border-white/[0.08] mt-3">
            <span className="text-xs text-slate-400 truncate font-mono">
              {app.imageName}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-4 border-t border-white/[0.08] pt-3 text-xs">
            <div>
              <span className="text-slate-400 block mb-0.5">Framework</span>
              <span className="text-slate-300 font-medium truncate">Docker</span>
            </div>
            <div>
              <span className="text-slate-400 block mb-0.5">Branch</span>
              <span className="text-slate-300 font-medium truncate">{branchLabel}</span>
            </div>
            <div>
              <span className="text-slate-400 block mb-0.5">Port</span>
              <span className="text-slate-300 font-medium truncate tabular-nums">
                {app.containerPort}
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between mt-3 text-xs text-slate-400">
            <span className="inline-flex items-center gap-1">
              <Clock className="h-3 w-3" />
              Deployed {formatRelative(app.updatedAt)}
            </span>
            {app.isSelfApp && app.latestKnownSha && app.commitSha !== app.latestKnownSha && (
              <button
                type="button"
                onClick={handleSelfUpdate}
                disabled={busy}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-300 text-xs font-medium border border-amber-500/20 hover:bg-amber-500/20 transition-colors disabled:opacity-60"
              >
                Update available
              </button>
            )}
            {commitsAhead.count && commitsAhead.count > 0 && (
              <a
                href={commitsAhead.compareUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-300 text-xs font-medium border border-purple-500/20 hover:bg-purple-500/20 transition-colors"
                onClick={(e: MouseEvent<HTMLAnchorElement>) =>
                  e.stopPropagation()
                }
                aria-label={`${commitsAhead.count} commits ahead on ${branchLabel}`}
              >
                <GitBranch className="h-3 w-3" />
                {commitsAhead.count} ahead
              </a>
            )}
          </div>
        </>
      )}
    </motion.article>
  );
}
