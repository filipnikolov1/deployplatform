"use client";

/**
 * OverviewTab — F5.2
 * Three sections: Info grid | Update banner + actions | Live stats
 */

import { useEffect, useRef, useState } from "react";
import {
  Container,
  Copy,
  Clock,
  ExternalLink,
  Github,
  GitBranch,
  GitCommit,
  Globe,
  Pencil,
  Plug,
  TrendingUp,
  ArrowRight,
  Check,
  X,
  type LucideIcon,
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import { Button } from "@/design/primitives/Button";
import { OperationProgress } from "@/design/OperationProgress";
import { toast } from "@/lib/toast";
import { useContainerStats } from "@/hooks/useContainerStats";
import { useCommitsAhead } from "@/hooks/useCommitsAhead";
import { useSelfAppPending } from "@/hooks/useSelfAppPending";
import { useAppConfig } from "@/hooks/useAppConfig";
import { getDisplayImageParts } from "@/lib/image-display";
import type { Deployment } from "@/types/deployment";
import type { DeploymentEvent } from "@/types/vector";
import { useSWRConfig } from "swr";

interface OverviewTabProps {
  app: Deployment;
  events: DeploymentEvent[];
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatUptime(uptimeSeconds: number): string {
  const days = Math.floor(uptimeSeconds / 86_400);
  const hours = Math.floor((uptimeSeconds % 86_400) / 3_600);
  const mins = Math.floor((uptimeSeconds % 3_600) / 60);
  const secs = uptimeSeconds % 60;
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${mins}m`;
  return `${mins}m ${secs}s`;
}

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

// ── Animated stat number ─────────────────────────────────────────────────────

function AnimatedNumber({ value }: { value: string }) {
  const [displayValue, setDisplayValue] = useState(value);
  const prevRef = useRef(value);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    const prev = prevRef.current;
    prevRef.current = value;
    if (prev === value) return;

    // Try to animate numerically; fall back to instant swap
    const prevNum = parseFloat(prev.replace(/[^0-9.]/g, ""));
    const nextNum = parseFloat(value.replace(/[^0-9.]/g, ""));
    if (isNaN(prevNum) || isNaN(nextNum)) {
      setDisplayValue(value);
      return;
    }

    const start = performance.now();
    const duration = 600;

    const tick = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      const interpolated = prevNum + (nextNum - prevNum) * eased;
      // Preserve the unit suffix
      const suffix = value.replace(/[\d.]+/, "");
      const decimals = value.includes(".") ? 1 : 0;
      setDisplayValue(interpolated.toFixed(decimals) + suffix);
      if (t < 1) rafRef.current = requestAnimationFrame(tick);
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [value]);

  return <span>{displayValue}</span>;
}

// ── Stat tile ────────────────────────────────────────────────────────────────

function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        padding: "14px 16px",
        background: M.surface,
        border: `1px solid ${M.line}`,
        borderRadius: M.rMd,
        display: "flex",
        flexDirection: "column",
        gap: 4,
      }}
    >
      <span
        style={{
          fontFamily: M.fontMono,
          fontSize: 9,
          fontWeight: 700,
          letterSpacing: "0.14em",
          textTransform: "uppercase",
          color: M.fg3,
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontFamily: M.fontMono,
          fontSize: 18,
          fontWeight: 600,
          color: M.fg,
          lineHeight: 1.2,
          letterSpacing: "-0.02em",
        }}
      >
        <AnimatedNumber value={value} />
      </span>
    </div>
  );
}

// ── Info row ─────────────────────────────────────────────────────────────────

function InfoRow({ icon: Icon, label, children }: {
  icon: LucideIcon;
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 13,
        minWidth: 0,
        paddingTop: 10,
        paddingBottom: 10,
        borderBottom: `1px solid ${M.line}`,
      }}
    >
      <Icon style={{ width: 13, height: 13, flexShrink: 0, color: M.fg3 }} />
      <span
        style={{
          width: 60,
          flexShrink: 0,
          fontSize: 12,
          fontFamily: M.fontSans,
          color: M.fg3,
          letterSpacing: "-0.005em",
        }}
      >
        {label}
      </span>
      <span
        style={{
          flex: 1,
          minWidth: 0,
          display: "flex",
          alignItems: "center",
          gap: 6,
          fontFamily: M.fontMono,
          fontSize: 12.5,
          color: M.fg,
          overflow: "hidden",
        }}
      >
        {children}
      </span>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function OverviewTab({ app, events }: OverviewTabProps) {
  const [subdomainEditing, setSubdomainEditing] = useState(false);
  const [subdomainInput, setSubdomainInput] = useState("");
  const [subdomainError, setSubdomainError] = useState<string | null>(null);
  const [subdomainSaving, setSubdomainSaving] = useState(false);
  const [restarting, setRestarting] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [showUpdateProgress, setShowUpdateProgress] = useState(false);
  const [activeUpdateOpId, setActiveUpdateOpId] = useState<string | null>(null);

  const { stats } = useContainerStats(app.appName);
  const { commitsAhead } = useCommitsAhead(app.appName);
  const { pending, refresh: refreshPending } = useSelfAppPending(
    app.isSelfApp ? app.appName : null
  );
  const { mutate } = useSWRConfig();

  // Check for UPDATE_AVAILABLE event
  const hasUpdate = app.isSelfApp && !!app.latestKnownSha && app.commitSha !== app.latestKnownSha;
  const isUpdating = !!pending || updating;

  const mutateApp = () => {
    void mutate("/api/apps");
    void mutate(`/api/apps/${encodeURIComponent(app.appName)}`);
  };

  useEffect(() => {
    if (pending) setUpdating(false);
  }, [pending]);

  const handleUpdate = async () => {
    if (updating || pending) return;
    setUpdating(true);
    try {
      const res = await fetch(`/api/self-apps/${encodeURIComponent(app.appName)}/update`, {
        method: "POST",
      });
      if (res.status === 409) {
        toast.info("Update already in progress");
        void refreshPending();
        return;
      }
      if (!res.ok) {
        toast.error("Update failed");
        setUpdating(false);
        return;
      }
      // Try to get operationId from response
      try {
        const json = await res.json() as { operationId?: string };
        if (json.operationId) {
          setActiveUpdateOpId(json.operationId);
          setShowUpdateProgress(true);
        }
      } catch {
        // ignore
      }
      toast.success("Update triggered");
      mutateApp();
      void refreshPending();
    } catch {
      toast.error("Update failed");
      setUpdating(false);
    }
  };

  const handleRestart = async () => {
    if (restarting) return;
    setRestarting(true);
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/restart`, {
        method: "POST",
      });
      if (res.status === 409) { toast.error("App is busy — try again in a moment"); return; }
      if (!res.ok) { toast.error("Restart failed"); return; }
      toast.success("Restart started");
      mutateApp();
    } catch {
      toast.error("Restart failed");
    } finally {
      setRestarting(false);
    }
  };

  const handleStop = async () => {
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/stop`, {
        method: "POST",
      });
      if (res.status === 409) { toast.error("App is busy — try again in a moment"); return; }
      if (!res.ok) { toast.error("Stop failed"); return; }
      toast.success(app.status === "RUNNING" ? "Container stopped" : "Container started");
      mutateApp();
    } catch {
      toast.error("Stop failed");
    }
  };

  const handleDelete = () => {
    toast.action(`Delete ${app.appName}?`, {
      label: "Confirm delete",
      onClick: async () => {
        try {
          const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}`, {
            method: "DELETE",
          });
          if (!res.ok) { toast.error("Delete failed"); return; }
          toast.success(`${app.appName} deleted`);
          mutateApp();
        } catch {
          toast.error("Delete failed");
        }
      },
      duration: 5 * 60 * 1000,
    });
  };

  const { appBaseDomain, scheme } = useAppConfig();
  const effectiveSubdomain = app.subdomain ?? app.appName;
  const publicUrl = `${scheme}://${effectiveSubdomain}.${appBaseDomain}`;
  const repoDisplay = app.repoUrl
    ? app.repoUrl.replace(/\.git$/, "").replace(/^https?:\/\/(github\.com\/)?/, "")
    : null;
  const repoHref = app.repoUrl ? app.repoUrl.replace(/\.git$/, "") : null;
  const displayImage = getDisplayImageParts(app);
  const commitHref = app.commitSha && app.repoUrl
    ? `${app.repoUrl.replace(/\.git$/, "")}/commit/${app.commitSha}`
    : null;

  const updateLabel = pending?.phase === "RECREATING"
    ? "Restarting container…"
    : pending?.phase === "PULLING"
      ? "Pulling image…"
      : updating
        ? "Starting update…"
        : "Update to latest";

  const activeOpId = activeUpdateOpId ?? pending?.operationId ?? null;

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 20,
        paddingTop: 4,
      }}
    >
      {/* ── Update banner ── */}
      <AnimatePresence>
        {(hasUpdate || isUpdating) && (
          <motion.div
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.22 }}
            style={{
              padding: 14,
              borderRadius: M.rMd,
              border: isUpdating
                ? `1px solid ${M.accentLine}`
                : `1px solid rgba(252,211,77,0.28)`,
              background: isUpdating
                ? M.accentSoft
                : "rgba(252,211,77,0.06)",
              display: "flex",
              flexDirection: "column",
              gap: 10,
            }}
            role="status"
            aria-live="polite"
          >
            {/* SHA row */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 8,
                flexWrap: "wrap",
                fontFamily: M.fontMono,
                fontSize: 12,
              }}
            >
              <span style={{ color: isUpdating ? M.fg2 : "rgba(252,211,77,0.7)" }}>running</span>
              <span style={{ color: isUpdating ? M.fg : M.warn }}>
                {app.commitSha?.slice(0, 7) ?? "—"}
              </span>
              <ArrowRight style={{ width: 11, height: 11, color: M.fg3 }} />
              <span style={{ color: isUpdating ? M.fg2 : "rgba(252,211,77,0.7)" }}>latest</span>
              <span style={{ color: isUpdating ? M.accent : M.warn }}>
                {app.latestKnownSha?.slice(0, 7) ?? "—"}
              </span>
              {!isUpdating && (
                <span
                  style={{
                    fontFamily: M.fontSans,
                    fontSize: 12,
                    color: "rgba(252,211,77,0.6)",
                    flex: 1,
                    minWidth: 0,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {app.latestKnownMessage}
                </span>
              )}
              {isUpdating && (
                <span
                  style={{
                    fontFamily: M.fontSans,
                    fontSize: 12,
                    color: M.accentLight,
                  }}
                >
                  {updateLabel}
                </span>
              )}
            </div>

            {/* Live progress bar slides in when updating */}
            <AnimatePresence>
              {(showUpdateProgress || isUpdating) && activeOpId && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: "auto", opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.22 }}
                  style={{ overflow: "hidden" }}
                >
                  <OperationProgress operationId={activeOpId} variant="banner" />
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Actions ── */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
        {(hasUpdate || isUpdating) && (
          <Button
            variant="accent"
            size="sm"
            onClick={handleUpdate}
            disabled={isUpdating}
          >
            {updateLabel}
          </Button>
        )}
        {!hasUpdate && !isUpdating && (
          <Button
            variant="secondary"
            size="sm"
            onClick={handleRestart}
            disabled={restarting}
          >
            {restarting ? "Restarting…" : "Restart"}
          </Button>
        )}
        <Button
          variant="secondary"
          size="sm"
          onClick={handleStop}
          disabled={app.status === "STOPPED"}
        >
          {app.status === "STOPPED" ? "Start" : "Stop"}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={handleDelete}
          style={{ color: M.err, marginLeft: "auto" }}
        >
          Delete
        </Button>
      </div>

      {/* ── Info grid ── */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
        }}
      >
        {/* Top border for first row */}
        <div style={{ borderTop: `1px solid ${M.line}` }} />

        {/* Image */}
        <InfoRow icon={Container} label="Image">
          <span
            style={{
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
              flex: 1,
            }}
          >
            {displayImage.repo}
          </span>
          {displayImage.tag && (
            <span
              style={{
                flexShrink: 0,
                padding: "2px 6px",
                borderRadius: M.rSm,
                fontSize: 10,
                background: M.surface2,
                border: `1px solid ${M.line2}`,
                color: M.fg2,
              }}
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
            style={{
              flexShrink: 0,
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              padding: 3,
              borderRadius: M.rSm,
              background: "transparent",
              border: "none",
              color: M.fg3,
              cursor: "pointer",
            }}
          >
            <Copy style={{ width: 11, height: 11 }} />
          </button>
        </InfoRow>

        {/* URL — non-self-apps only */}
        {!app.isSelfApp && (
          <InfoRow icon={Globe} label="URL">
            {subdomainEditing ? (
              <span
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: 4,
                  flex: 1,
                  minWidth: 0,
                }}
              >
                <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <input
                    autoFocus
                    value={subdomainInput}
                    onChange={(e) => {
                      setSubdomainInput(e.target.value);
                      setSubdomainError(null);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === "Escape") {
                        setSubdomainEditing(false);
                        setSubdomainError(null);
                      }
                    }}
                    style={{
                      flex: 1,
                      minWidth: 0,
                      padding: "3px 6px",
                      borderRadius: M.rSm,
                      fontSize: 12,
                      fontFamily: M.fontMono,
                      background: M.surface2,
                      border: `1px solid ${M.line2}`,
                      color: M.fg,
                      outline: "none",
                    }}
                    placeholder={app.appName}
                  />
                  <span style={{ flexShrink: 0, fontSize: 10, color: M.fg3 }}>.{appBaseDomain}</span>
                  <button
                    type="button"
                    disabled={subdomainSaving}
                    aria-label="Save subdomain"
                    onClick={async () => {
                      const val = subdomainInput.trim();
                      const SUBDOMAIN_RE = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/;
                      if (val !== "" && !SUBDOMAIN_RE.test(val)) {
                        setSubdomainError("Only lowercase letters, digits, hyphens; must start/end with alphanumeric");
                        return;
                      }
                      setSubdomainSaving(true);
                      try {
                        const res = await fetch(
                          `/api/apps/${encodeURIComponent(app.appName)}/subdomain`,
                          {
                            method: "PATCH",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ subdomain: val || null }),
                          }
                        );
                        if (!res.ok) {
                          const data = await res.json().catch(() => ({}));
                          const msg = (data as { error?: string }).error ?? "Failed to update subdomain";
                          toast.error(msg);
                        } else {
                          toast.success("Subdomain updated");
                          setSubdomainEditing(false);
                          mutateApp();
                        }
                      } catch {
                        toast.error("Failed to update subdomain");
                      } finally {
                        setSubdomainSaving(false);
                      }
                    }}
                    style={{
                      flexShrink: 0,
                      display: "inline-flex",
                      alignItems: "center",
                      padding: 3,
                      borderRadius: M.rSm,
                      background: "transparent",
                      border: "none",
                      color: M.ok,
                      cursor: "pointer",
                    }}
                  >
                    <Check style={{ width: 13, height: 13 }} />
                  </button>
                  <button
                    type="button"
                    aria-label="Cancel"
                    onClick={() => { setSubdomainEditing(false); setSubdomainError(null); }}
                    style={{
                      flexShrink: 0,
                      display: "inline-flex",
                      alignItems: "center",
                      padding: 3,
                      borderRadius: M.rSm,
                      background: "transparent",
                      border: "none",
                      color: M.fg3,
                      cursor: "pointer",
                    }}
                  >
                    <X style={{ width: 13, height: 13 }} />
                  </button>
                </span>
                {subdomainError && (
                  <span style={{ fontSize: 11, color: M.err }}>{subdomainError}</span>
                )}
              </span>
            ) : (
              <>
                <a
                  href={publicUrl}
                  target="_blank"
                  rel="noreferrer"
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 4,
                    color: M.accent,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                    flex: 1,
                    textDecoration: "none",
                    minWidth: 0,
                  }}
                >
                  <span
                    style={{
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {publicUrl.replace(/^https?:\/\//, "")}
                  </span>
                  <ExternalLink style={{ width: 11, height: 11, flexShrink: 0 }} />
                </a>
                <button
                  type="button"
                  aria-label="Edit subdomain"
                  onClick={() => {
                    setSubdomainInput(app.subdomain ?? "");
                    setSubdomainError(null);
                    setSubdomainEditing(true);
                  }}
                  style={{
                    flexShrink: 0,
                    display: "inline-flex",
                    alignItems: "center",
                    padding: 3,
                    borderRadius: M.rSm,
                    background: "transparent",
                    border: "none",
                    color: M.fg3,
                    cursor: "pointer",
                  }}
                >
                  <Pencil style={{ width: 11, height: 11 }} />
                </button>
              </>
            )}
          </InfoRow>
        )}

        {/* Branch */}
        <InfoRow icon={GitBranch} label="Branch">
          {app.branch ? (
            <span style={{ fontStyle: "italic" }}>{app.branch}</span>
          ) : (
            <span style={{ color: M.fg4 }}>—</span>
          )}
        </InfoRow>

        {/* Commit */}
        <InfoRow icon={GitCommit} label="Commit">
          {app.commitSha ? (
            commitHref ? (
              <a
                href={commitHref}
                target="_blank"
                rel="noreferrer"
                style={{
                  flexShrink: 0,
                  padding: "2px 6px",
                  borderRadius: M.rSm,
                  fontSize: 10.5,
                  background: M.surface2,
                  border: `1px solid ${M.line2}`,
                  color: M.accent,
                  textDecoration: "none",
                }}
              >
                {app.commitSha.slice(0, 7)}
              </a>
            ) : (
              <span
                style={{
                  flexShrink: 0,
                  padding: "2px 6px",
                  borderRadius: M.rSm,
                  fontSize: 10.5,
                  background: M.surface2,
                  border: `1px solid ${M.line2}`,
                  color: M.fg,
                }}
              >
                {app.commitSha.slice(0, 7)}
              </span>
            )
          ) : (
            <span style={{ color: M.fg4 }}>—</span>
          )}
          {app.commitMessage && (
            <span
              style={{
                flex: 1,
                minWidth: 0,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                fontSize: 12,
                fontFamily: M.fontSans,
                color: M.fg3,
              }}
              title={app.commitMessage}
            >
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
              style={{
                color: M.accent,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                flex: 1,
                textDecoration: "none",
              }}
            >
              {repoDisplay}
            </a>
          ) : (
            <span style={{ color: M.fg4 }}>—</span>
          )}
        </InfoRow>

        {/* Port */}
        <InfoRow icon={Plug} label="Port">
          <span>:{app.containerPort}</span>
        </InfoRow>

        {/* Deployed */}
        <InfoRow icon={Clock} label="Deployed">
          <span title={new Date(app.updatedAt).toLocaleString()}>
            {relativeTime(app.updatedAt)}
          </span>
        </InfoRow>

        {/* Commits ahead */}
        {commitsAhead?.count != null && commitsAhead.count > 0 && (
          <InfoRow icon={TrendingUp} label="Ahead">
            <a
              href={commitsAhead.compareUrl}
              target="_blank"
              rel="noreferrer"
              style={{ color: M.warn, textDecoration: "none" }}
            >
              {commitsAhead.count} commits
            </a>
          </InfoRow>
        )}
      </div>

      {/* ── Live stats ── */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: 8,
        }}
      >
        <StatTile label="CPU" value={stats ? `${stats.cpuPercent.toFixed(1)}%` : "—"} />
        <StatTile
          label="Memory"
          value={stats ? `${Math.round(stats.memoryUsedMB)}MB` : "—"}
        />
        <StatTile label="Uptime" value={stats ? formatUptime(stats.uptimeSeconds) : "—"} />
        <StatTile label="Restarts" value={stats ? String(stats.restartCount) : "—"} />
      </div>
    </div>
  );
}
