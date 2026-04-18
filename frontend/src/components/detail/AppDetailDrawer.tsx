"use client";

import { useState, useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import type { ComponentType } from "react";
import useSWR, { useSWRConfig } from "swr";
import {
  ArrowRight,
  ArrowUpCircle,
  Check,
  Clock,
  Container,
  Copy,
  Cpu,
  ExternalLink,
  Github,
  GitBranch,
  GitCommit,
  Globe,
  HardDrive,
  Pencil,
  Plug,
  RotateCcw,
  TrendingUp,
  X,
  type LucideIcon,
} from "lucide-react";
import { useToast } from "@/hooks/useToast";
import { useEvents } from "@/hooks/useEvents";
import { useContainerStats } from "@/hooks/useContainerStats";
import { useCommitsAhead } from "@/hooks/useCommitsAhead";
import { BuildLogViewer } from "./BuildLogViewer";
import { EnvVarsTab } from "./EnvVarsTab";
import { DeployHistoryList } from "./DeployHistoryList";
import { ActivityTimeline } from "@/components/activity/ActivityTimeline";
import { AppDetailSkeleton } from "./AppDetailSkeleton";
import { getDisplayImageParts } from "@/lib/image-display";
import { statusConfig, toAppStatus } from "@/lib/statusConfig";
import { clearBackendUpdatePending, markBackendUpdatePending } from "@/lib/self-update";
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

function formatUptime(uptimeSeconds: number): string {
  const days = Math.floor(uptimeSeconds / 86_400);
  const hours = Math.floor((uptimeSeconds % 86_400) / 3_600);
  const mins = Math.floor((uptimeSeconds % 3_600) / 60);
  const secs = uptimeSeconds % 60;
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${mins}m`;
  return `${mins}m ${secs}s`;
}

/** Stat card in the telemetry strip */
function StatCard({ icon: Icon, label, value }: { icon: ComponentType<{ className?: string; style?: React.CSSProperties }>; label: string; value: string }) {
  return (
    <div>
      <div className="flex items-center gap-1.5 mb-1">
        <Icon className="h-3 w-3" style={{ color: "var(--c-fg-3)" }} />
        <span
          className="text-[10px] uppercase tracking-[0.12em]"
          style={{ color: "var(--c-fg-3)" }}
        >
          {label}
        </span>
      </div>
      <div
        className="text-sm font-medium tabular-nums"
        style={{ color: "var(--c-fg-1)" }}
      >
        {value}
      </div>
    </div>
  );
}

function InfoRow({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2 text-xs min-w-0">
      <Icon className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--c-fg-3)" }} />
      <span className="w-14 shrink-0" style={{ color: "var(--c-fg-3)" }}>{label}</span>
      <span className="min-w-0 flex-1 flex items-center gap-2 truncate" style={{ color: "var(--c-fg-2)" }}>{children}</span>
    </div>
  );
}

const TABS = [
  { id: "logs", label: "Logs" },
  { id: "env", label: "Env Vars" },
  { id: "activity", label: "Activity" },
  { id: "history", label: "History" },
] as const;

type TabId = (typeof TABS)[number]["id"];

export function AppDetailDrawer({ appName, onClose }: Props) {
  const drawerRef = useRef<HTMLElement>(null);
  const [tab, setTab] = useState<TabId>("logs");
  const [downloadingLogs, setDownloadingLogs] = useState(false);
  const [restarting, setRestarting] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [editingSubdomain, setEditingSubdomain] = useState(false);
  const [subdomainInput, setSubdomainInput] = useState("");
  const [subdomainError, setSubdomainError] = useState<string | null>(null);
  const [savingSubdomain, setSavingSubdomain] = useState(false);

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

  const titleId = `drawer-${appName}`;

  // Keyboard & scroll-lock
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      document.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  const mutateApp = () => {
    void mutate("/api/apps");
    void mutate(`/api/apps/${encodeURIComponent(appName)}`);
  };

  const handleRestart = async () => {
    if (restarting || !app) return;
    setRestarting(true);
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/restart`, { method: "POST" });
      if (res.status === 409) { toast.error("App is busy — try again in a moment"); return; }
      if (!res.ok) { toast.error("Restart failed"); return; }
      toast.success("Restart started");
      mutateApp();
    } catch { toast.error("Restart failed"); }
    finally { setRestarting(false); }
  };

  const handleStop = async () => {
    if (!app) return;
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/stop`, { method: "POST" });
      if (res.status === 409) { toast.error("App is busy — try again in a moment"); return; }
      if (!res.ok) { toast.error("Stop failed"); return; }
      toast.success("Container stopped");
      mutateApp();
    } catch { toast.error("Stop failed"); }
  };

  const handleUpdate = async () => {
    if (updating || !app) return;
    setUpdating(true);
    try {
      if (app.appName === "launchpad-backend") {
        markBackendUpdatePending();
      }
      const res = await fetch(`/api/self-apps/${encodeURIComponent(app.appName)}/update`, { method: "POST" });
      if (!res.ok) {
        if (app.appName === "launchpad-backend") {
          clearBackendUpdatePending();
        }
        toast.error("Update failed");
        setUpdating(false);
        return;
      }
      toast.success("Update triggered — restarting");
      mutateApp();
      setTimeout(() => {
        setUpdating(false);
        onClose();
      }, 1500);
    } catch {
      if (app.appName === "launchpad-backend") {
        clearBackendUpdatePending();
      }
      toast.error("Update failed");
      setUpdating(false);
    }
  };

  const handleDownloadLogs = async () => {
    if (downloadingLogs || !app) return;
    setDownloadingLogs(true);
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/logs/runtime/download`);
      if (!res.ok) { toast.error("Log download failed"); return; }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${app.appName}-runtime.log`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch { toast.error("Log download failed"); }
    finally { setDownloadingLogs(false); }
  };

  const hasUpdate = app?.isSelfApp && !!app.latestKnownSha && app.commitSha !== app.latestKnownSha;

  const status = app ? toAppStatus(app.status) : "STOPPED";
  const config = statusConfig[status];

  const dotColor = status === "RUNNING" ? "#22C55E"
    : status === "BUILDING" ? "#F59E0B"
    : status === "FAILED" || status === "CRASHED" ? "#EF4444"
    : "#64748B";
  const chipFg = status === "RUNNING" ? "#86EFAC"
    : status === "BUILDING" ? "#FCD34D"
    : status === "FAILED" || status === "CRASHED" ? "#FCA5A5"
    : "#CBD5E1";
  const chipBg = status === "RUNNING" ? "rgba(34,197,94,0.10)"
    : status === "BUILDING" ? "rgba(245,158,11,0.10)"
    : status === "FAILED" || status === "CRASHED" ? "rgba(239,68,68,0.10)"
    : "rgba(100,116,139,0.10)";
  const chipBd = status === "RUNNING" ? "rgba(34,197,94,0.24)"
    : status === "BUILDING" ? "rgba(245,158,11,0.28)"
    : status === "FAILED" || status === "CRASHED" ? "rgba(239,68,68,0.28)"
    : "rgba(100,116,139,0.24)";
  const iconBg = status === "RUNNING" ? "rgba(34,197,94,0.10)"
    : status === "BUILDING" ? "rgba(245,158,11,0.10)"
    : status === "FAILED" || status === "CRASHED" ? "rgba(239,68,68,0.10)"
    : "rgba(100,116,139,0.10)";
  const iconBd = status === "RUNNING" ? "rgba(34,197,94,0.24)"
    : status === "BUILDING" ? "rgba(245,158,11,0.28)"
    : status === "FAILED" || status === "CRASHED" ? "rgba(239,68,68,0.28)"
    : "rgba(100,116,139,0.24)";

  const Icon = config.icon;

  if (typeof document === "undefined") return null;

  return createPortal(
    /* Backdrop */
    <div
      className="fixed inset-0 z-[100] lp-fadein"
      style={{ background: "rgba(0,0,0,0.4)", backdropFilter: "blur(2px)" }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      role="presentation"
    >
      <aside
        ref={drawerRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="absolute top-0 right-0 bottom-0 flex flex-col lp-slidein"
        style={{
          width: "min(640px, 100vw)",
          background: "#0A0A12",
          borderLeft: "1px solid var(--c-border-2)",
          boxShadow: "-24px 0 60px rgba(0,0,0,0.5)",
        }}
      >
        {!app ? (
          <AppDetailSkeleton />
        ) : (
          <>
            {/* ── Header ── */}
            <header
              className="flex flex-col px-6 py-[18px] gap-3"
              style={{ borderBottom: "1px solid var(--c-border-1)" }}
            >
              {/* Title row */}
              <div className="flex items-start gap-3.5">
                <div
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md"
                  style={{ background: iconBg, border: `1px solid ${iconBd}`, color: chipFg }}
                >
                  <Icon className="h-[18px] w-[18px]" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2
                      id={titleId}
                      className="text-lg font-semibold tracking-[-0.005em] m-0"
                      style={{ color: "var(--c-fg-0)" }}
                    >
                      {app.appName}
                    </h2>
                    {/* Status chip */}
                    <span
                      className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10.5px] font-medium whitespace-nowrap"
                      style={{ color: chipFg, background: chipBg, border: `1px solid ${chipBd}` }}
                    >
                      <span
                        className="h-1.5 w-1.5 rounded-full"
                        style={{ background: dotColor, boxShadow: `0 0 4px ${dotColor}` }}
                      />
                      {config.label}
                    </span>
                    {app.isSelfApp && (
                      <span
                        className="text-[10.5px] font-semibold px-2 py-0.5 rounded-full uppercase tracking-[0.04em]"
                        style={{
                          color: "#DDD6FE",
                          background: "var(--c-ghost-soft)",
                          border: "1px solid var(--c-ghost-line)",
                        }}
                      >
                        Self-app
                      </span>
                    )}
                  </div>
                </div>
                <button
                  type="button"
                  aria-label="Close drawer"
                  onClick={onClose}
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md transition-colors duration-[120ms] outline-none focus-visible:ring-2 focus-visible:ring-accent-ghostLight"
                  style={{
                    background: "var(--c-surface-2)",
                    border: "1px solid var(--c-border-1)",
                    color: "var(--c-fg-2)",
                  }}
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              {/* ── Info grid ── */}
              {(() => {
                const baseDomain = process.env.NEXT_PUBLIC_APP_BASE_DOMAIN ?? "localhost";
                const effectiveSubdomain = app.subdomain ?? app.appName;
                const publicUrl = `http://${effectiveSubdomain}.${baseDomain}`;
                const repoDisplay = app.repoUrl
                  ? app.repoUrl.replace(/\.git$/, "").replace(/^https?:\/\/(github\.com\/)?/, "")
                  : null;
                const repoHref = app.repoUrl ? app.repoUrl.replace(/\.git$/, "") : null;
                const displayImage = getDisplayImageParts(app);
                const commitHref = app.commitSha && app.repoUrl
                  ? `${app.repoUrl.replace(/\.git$/, "")}/commit/${app.commitSha}`
                  : null;
                return (
                  <div
                    className="grid grid-cols-1 gap-1.5 sm:grid-cols-2 pt-3"
                    style={{ borderTop: "1px solid var(--c-border-1)" }}
                  >
                    {/* Image */}
                    <InfoRow icon={Container} label="Image">
                      <span className="font-mono truncate">{displayImage.repo}</span>
                      {displayImage.tag && (
                        <span
                          className="shrink-0 font-mono rounded px-1.5 py-0.5 text-[10px]"
                          style={{ background: "var(--c-surface-2)", border: "1px solid var(--c-border-2)" }}
                        >
                          {displayImage.tag}
                        </span>
                      )}
                      <button
                        type="button"
                        aria-label="Copy image name"
                        onClick={() => {
                          void navigator.clipboard.writeText(displayImage.full);
                          toast.success("Copied");
                        }}
                        className="shrink-0 flex items-center justify-center rounded p-0.5 transition-colors hover:bg-white/10"
                        style={{ color: "var(--c-fg-3)" }}
                      >
                        <Copy className="h-3 w-3" />
                      </button>
                    </InfoRow>

                    {/* Page URL — hidden for self-apps */}
                    {!app.isSelfApp && (
                      <InfoRow icon={Globe} label="URL">
                        {editingSubdomain ? (
                          <span className="flex flex-col gap-1 flex-1 min-w-0">
                            <span className="flex items-center gap-1.5">
                              <input
                                autoFocus
                                value={subdomainInput}
                                onChange={(e) => {
                                  setSubdomainInput(e.target.value);
                                  setSubdomainError(null);
                                }}
                                onKeyDown={(e) => {
                                  if (e.key === "Escape") {
                                    setEditingSubdomain(false);
                                    setSubdomainError(null);
                                  }
                                }}
                                className="flex-1 min-w-0 rounded px-2 py-0.5 text-xs font-mono outline-none focus-visible:ring-1 focus-visible:ring-accent-ghostLight"
                                style={{
                                  background: "var(--c-surface-2)",
                                  border: "1px solid var(--c-border-2)",
                                  color: "var(--c-fg-1)",
                                }}
                                placeholder={app.appName}
                              />
                              <span className="shrink-0 text-[10px]" style={{ color: "var(--c-fg-3)" }}>.{baseDomain}</span>
                              <button
                                type="button"
                                disabled={savingSubdomain}
                                onClick={async () => {
                                  const val = subdomainInput.trim();
                                  const SUBDOMAIN_RE = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/;
                                  if (val !== "" && !SUBDOMAIN_RE.test(val)) {
                                    setSubdomainError("Only lowercase letters, digits, and hyphens; must start and end with alphanumeric");
                                    return;
                                  }
                                  setSavingSubdomain(true);
                                  try {
                                    const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/subdomain`, {
                                      method: "PATCH",
                                      headers: { "Content-Type": "application/json" },
                                      body: JSON.stringify({ subdomain: val || null }),
                                    });
                                    if (!res.ok) {
                                      const data = await res.json().catch(() => ({}));
                                      const msg = (data as { error?: string }).error ?? "Failed to update subdomain";
                                      toast.error(msg);
                                    } else {
                                      toast.success("Subdomain updated");
                                      setEditingSubdomain(false);
                                      mutateApp();
                                    }
                                  } catch {
                                    toast.error("Failed to update subdomain");
                                  } finally {
                                    setSavingSubdomain(false);
                                  }
                                }}
                                className="shrink-0 flex items-center justify-center rounded p-0.5 transition-colors hover:bg-white/10"
                                style={{ color: "#86EFAC" }}
                                aria-label="Save subdomain"
                              >
                                <Check className="h-3.5 w-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => { setEditingSubdomain(false); setSubdomainError(null); }}
                                className="shrink-0 flex items-center justify-center rounded p-0.5 transition-colors hover:bg-white/10"
                                style={{ color: "var(--c-fg-3)" }}
                                aria-label="Cancel"
                              >
                                <X className="h-3.5 w-3.5" />
                              </button>
                            </span>
                            {subdomainError && (
                              <span className="text-[10px]" style={{ color: "#FCA5A5" }}>{subdomainError}</span>
                            )}
                          </span>
                        ) : (
                          <>
                            <a
                              href={publicUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-1 truncate hover:opacity-80"
                              style={{ color: "#C4B5FD" }}
                            >
                              {publicUrl.replace(/^https?:\/\//, "")}
                              <ExternalLink className="h-3 w-3 shrink-0" />
                            </a>
                            <button
                              type="button"
                              aria-label="Edit subdomain"
                              onClick={() => {
                                setSubdomainInput(app.subdomain ?? "");
                                setSubdomainError(null);
                                setEditingSubdomain(true);
                              }}
                              className="shrink-0 flex items-center justify-center rounded p-0.5 transition-colors hover:bg-white/10"
                              style={{ color: "var(--c-fg-3)" }}
                            >
                              <Pencil className="h-3 w-3" />
                            </button>
                          </>
                        )}
                      </InfoRow>
                    )}

                    {/* Branch */}
                    <InfoRow icon={GitBranch} label="Branch">
                      {app.branch ? (
                        <span>{app.branch}</span>
                      ) : (
                        <span className="text-white/30">—</span>
                      )}
                    </InfoRow>

                    {/* Last commit */}
                    <InfoRow icon={GitCommit} label="Commit">
                      {app.commitSha ? (
                        commitHref ? (
                          <a
                            href={commitHref}
                            target="_blank"
                            rel="noreferrer"
                            className="font-mono shrink-0 rounded px-1.5 py-0.5 text-[10px] hover:opacity-80"
                            style={{ background: "var(--c-surface-2)", border: "1px solid var(--c-border-2)", color: "#C4B5FD" }}
                          >
                            {app.commitSha.slice(0, 7)}
                          </a>
                        ) : (
                          <span
                            className="font-mono shrink-0 rounded px-1.5 py-0.5 text-[10px]"
                            style={{ background: "var(--c-surface-2)", border: "1px solid var(--c-border-2)" }}
                          >
                            {app.commitSha.slice(0, 7)}
                          </span>
                        )
                      ) : (
                        <span className="text-white/30">—</span>
                      )}
                      {app.commitMessage && (
                        <span className="truncate" title={app.commitMessage} style={{ color: "var(--c-fg-3)" }}>
                          {app.commitMessage}
                        </span>
                      )}
                    </InfoRow>

                    {/* Repo */}
                    <InfoRow icon={Github} label="Repo">
                      {repoHref ? (
                        <a
                          href={repoHref}
                          target="_blank"
                          rel="noreferrer"
                          className="truncate hover:opacity-80"
                          style={{ color: "#C4B5FD" }}
                        >
                          {repoDisplay}
                        </a>
                      ) : (
                        <span className="text-white/30">—</span>
                      )}
                    </InfoRow>

                    {/* Port */}
                    <InfoRow icon={Plug} label="Port">
                      <span className="font-mono">:{app.containerPort}</span>
                    </InfoRow>

                    {/* Deployed */}
                    <InfoRow icon={Clock} label="Deployed">
                      <span title={new Date(app.updatedAt).toLocaleString()}>
                        {(() => {
                          const diff = Date.now() - new Date(app.updatedAt).getTime();
                          const mins = Math.floor(diff / 60_000);
                          if (mins < 1) return "just now";
                          if (mins < 60) return `${mins}m ago`;
                          const hrs = Math.floor(mins / 60);
                          if (hrs < 24) return `${hrs}h ago`;
                          return `${Math.floor(hrs / 24)}d ago`;
                        })()}
                      </span>
                    </InfoRow>

                    {/* Commits ahead — self-apps only, when count > 0 */}
                    {commitsAhead && commitsAhead.count != null && commitsAhead.count > 0 ? (
                      <InfoRow icon={TrendingUp} label="Ahead">
                        <a
                          href={commitsAhead.compareUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="hover:opacity-80"
                          style={{ color: "#FDE68A" }}
                        >
                          {commitsAhead.count} commits
                        </a>
                      </InfoRow>
                    ) : null}
                  </div>
                );
              })()}
            </header>

            {/* ── Actions strip ── */}
            <div
              className="flex flex-wrap items-center gap-2 px-6 py-3"
              style={{ borderBottom: "1px solid var(--c-border-1)" }}
            >
              {hasUpdate ? (
                <ActionButton
                  onClick={handleUpdate}
                  disabled={updating}
                  loading={updating}
                  variant="primary"
                >
                  {updating ? "Updating…" : "Update to latest"}
                </ActionButton>
              ) : (
                <ActionButton
                  onClick={handleRestart}
                  disabled={restarting}
                  loading={restarting}
                  variant="primary"
                >
                  {restarting ? "Restarting…" : "Redeploy"}
                </ActionButton>
              )}
              <ActionButton
                onClick={handleStop}
                disabled={app.status === "STOPPED"}
                variant="secondary"
              >
                {app.status === "STOPPED" ? "Start" : "Stop"}
              </ActionButton>
              <ActionButton
                onClick={handleDownloadLogs}
                disabled={downloadingLogs}
                variant="ghost"
                className="ml-auto"
              >
                {downloadingLogs ? "Preparing…" : "Download logs"}
              </ActionButton>
            </div>

            {/* ── Update available banner ── */}
            {hasUpdate && (
              <div
                role="status"
                aria-live="polite"
                className="mx-6 mt-4 flex items-center gap-4 rounded-[10px] p-4"
                style={{
                  background: "rgba(245,158,11,0.08)",
                  border: "1px solid rgba(245,158,11,0.25)",
                }}
              >
                <div
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
                  style={{
                    background: "rgba(245,158,11,0.15)",
                    border: "1px solid rgba(245,158,11,0.30)",
                  }}
                >
                  <ArrowUpCircle className="h-5 w-5 text-amber-200" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-semibold text-amber-100">Update available</div>
                  <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-mono">
                    <span className="text-amber-100/60">running</span>
                    <span className="text-amber-100/90">{app.commitSha?.slice(0, 7) ?? "—"}</span>
                    <ArrowRight className="h-3 w-3 text-amber-100/50" />
                    <span className="text-amber-100/60">latest</span>
                    <span className="text-amber-100">{app.latestKnownSha?.slice(0, 7)}</span>
                  </div>
                  {app.latestKnownMessage && (
                    <div className="mt-1 truncate text-xs text-amber-100/70 font-sans">{app.latestKnownMessage}</div>
                  )}
                </div>
              </div>
            )}

            {/* ── Telemetry strip ── */}
            <div
              className="grid grid-cols-4 gap-3 px-6 py-3.5"
              style={{ borderBottom: "1px solid var(--c-border-1)" }}
            >
              <StatCard icon={Cpu} label="CPU" value={stats ? `${stats.cpuPercent.toFixed(1)}%` : "—"} />
              <StatCard
                icon={HardDrive}
                label="Memory"
                value={stats ? `${Math.round(stats.memoryUsedMB)}MB / ${stats.memoryLimitMB}MB` : "—"}
              />
              <StatCard icon={Clock} label="Uptime" value={stats ? formatUptime(stats.uptimeSeconds) : "—"} />
              <StatCard icon={RotateCcw} label="Restarts" value={stats ? String(stats.restartCount) : "—"} />
            </div>

            {/* ── Tabs ── */}
            <div className="px-6 pt-3.5 pb-0">
              <div
                className="inline-flex gap-0.5 rounded-md p-[3px]"
                style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
              >
                {TABS.map((t) => {
                  const sel = t.id === tab;
                  return (
                    <button
                      key={t.id}
                      type="button"
                      role="tab"
                      aria-selected={sel}
                      onClick={() => setTab(t.id)}
                      className="rounded-[5px] px-3.5 py-1.5 text-[13px] font-medium transition-all duration-[120ms] outline-none focus-visible:ring-2 focus-visible:ring-accent-ghostLight"
                      style={{
                        background: sel ? "var(--c-ghost-soft)" : "transparent",
                        color: sel ? "#DDD6FE" : "var(--c-fg-2)",
                        border: "none",
                        cursor: "pointer",
                      }}
                    >
                      {t.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* ── Tab panels ── */}
            <div className={`flex-1 min-h-0 px-6 py-4 ${tab === "logs" ? "overflow-hidden" : "overflow-auto"}`}>
              {tab === "logs" && (
                <BuildLogViewer appName={app.appName} />
              )}
              {tab === "env" && (
                <EnvVarsTab appName={app.appName} />
              )}
              {tab === "activity" && (
                events.length === 0 ? (
                  <div className="text-sm" style={{ color: "var(--c-fg-3)" }}>No recent activity.</div>
                ) : (
                  <ActivityTimeline events={events} />
                )
              )}
              {tab === "history" && (
                <DeployHistoryList appName={app.appName} events={events} currentImage={app.imageName} />
              )}
            </div>
          </>
        )}
      </aside>
    </div>,
    document.body,
  );
}

/** Small inline action button for the drawer action strip */
function ActionButton({
  children,
  variant = "secondary",
  disabled,
  loading,
  onClick,
  className = "",
}: {
  children: React.ReactNode;
  variant?: "primary" | "secondary" | "ghost";
  disabled?: boolean;
  loading?: boolean;
  onClick?: () => void;
  className?: string;
}) {
  const styles = {
    primary: {
      background: "var(--c-ghost-soft)",
      color: "#DDD6FE",
      border: "1px solid var(--c-ghost-line)",
    },
    secondary: {
      background: "var(--c-surface-2)",
      color: "var(--c-fg-1)",
      border: "1px solid var(--c-border-2)",
    },
    ghost: {
      background: "transparent",
      color: "var(--c-fg-2)",
      border: "1px solid transparent",
    },
  }[variant];

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || loading}
      className={`inline-flex items-center gap-2 rounded-md px-3.5 py-1.5 text-[13px] font-medium transition-[filter] duration-[120ms] outline-none focus-visible:ring-2 focus-visible:ring-accent-ghostLight ${className}`}
      style={{
        ...styles,
        cursor: disabled || loading ? "not-allowed" : "pointer",
        opacity: disabled || loading ? 0.6 : 1,
      }}
    >
      {loading && (
        <span
          className="inline-block h-3.5 w-3.5 rounded-full border-2 border-current border-t-transparent"
          style={{ animation: "lp-spin 1s linear infinite" }}
        />
      )}
      {children}
    </button>
  );
}
