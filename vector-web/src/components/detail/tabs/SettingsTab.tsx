"use client";

/**
 * SettingsTab — F5.6
 * Sections: Subdomain | Pin | Notifications | Danger zone
 */

import { useState } from "react";
import { useSWRConfig } from "swr";
import { motion } from "framer-motion";
import { M } from "@/design/tokens";
import { Button } from "@/design/primitives/Button";
import { toast } from "@/lib/toast";
import { useAppConfig } from "@/hooks/useAppConfig";
import type { Deployment } from "@/types/deployment";
import type { UserPreferences } from "@/types/vector";

interface SettingsTabProps {
  app: Deployment;
  preferences?: UserPreferences | null;
  onClose?: () => void;
}

function SectionHeader({ title }: { title: string }) {
  return (
    <div
      style={{
        fontFamily: M.fontMono,
        fontSize: 10,
        fontWeight: 700,
        letterSpacing: "0.14em",
        textTransform: "uppercase",
        color: M.fg3,
        marginBottom: 10,
      }}
    >
      {title}
    </div>
  );
}

function ToggleSwitch({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      style={{
        width: 36,
        height: 20,
        borderRadius: M.rPill,
        background: checked ? M.accent : M.surface2,
        border: `1px solid ${checked ? M.accentLine : M.line2}`,
        cursor: "pointer",
        position: "relative",
        outline: "none",
        flexShrink: 0,
        transition: "background 150ms",
      }}
    >
      <motion.span
        animate={{ x: checked ? 17 : 1 }}
        transition={{ type: "spring", stiffness: 380, damping: 32 }}
        style={{
          display: "block",
          width: 16,
          height: 16,
          borderRadius: "50%",
          background: M.fg,
          position: "absolute",
          top: 1,
        }}
      />
    </button>
  );
}

export function SettingsTab({ app, preferences, onClose }: SettingsTabProps) {
  const { mutate } = useSWRConfig();

  // ── Subdomain ──────────────────────────────────────────────────────────────
  const { appBaseDomain: baseDomain } = useAppConfig();
  const [subdomain, setSubdomain] = useState(app.subdomain ?? "");
  const [subdomainSaving, setSubdomainSaving] = useState(false);
  const [subdomainError, setSubdomainError] = useState<string | null>(null);

  const handleSubdomainSave = async () => {
    const val = subdomain.trim();
    const SUBDOMAIN_RE = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/;
    if (val !== "" && !SUBDOMAIN_RE.test(val)) {
      setSubdomainError("Only lowercase letters, digits, and hyphens; must start and end with alphanumeric");
      return;
    }
    setSubdomainError(null);
    setSubdomainSaving(true);
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
        void mutate("/api/apps");
        void mutate(`/api/apps/${encodeURIComponent(app.appName)}`);
      }
    } catch {
      toast.error("Failed to update subdomain");
    } finally {
      setSubdomainSaving(false);
    }
  };

  // ── Pin ────────────────────────────────────────────────────────────────────
  const isPinned = !!app.pinnedImage;
  const [pinning, setPinning] = useState(false);

  const handlePin = async () => {
    setPinning(true);
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/pin`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ commitSha: app.commitSha }),
      });
      if (!res.ok) { toast.error("Not yet wired"); return; }
      toast.success("Pinned to current commit");
      void mutate(`/api/apps/${encodeURIComponent(app.appName)}`);
    } catch {
      toast.error("Not yet wired");
    } finally {
      setPinning(false);
    }
  };

  const handleUnpin = async () => {
    setPinning(true);
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}/pin`, {
        method: "DELETE",
      });
      if (!res.ok) { toast.error("Not yet wired"); return; }
      toast.success("Unpinned — auto-deploy resumed");
      void mutate(`/api/apps/${encodeURIComponent(app.appName)}`);
    } catch {
      toast.error("Not yet wired");
    } finally {
      setPinning(false);
    }
  };

  // ── Notifications ──────────────────────────────────────────────────────────
  const [notifyOnCrash, setNotifyOnCrash] = useState(
    preferences?.notify_on_crash ?? false
  );
  const [notifySaving, setNotifySaving] = useState(false);

  const handleNotifyToggle = async (val: boolean) => {
    setNotifyOnCrash(val);
    setNotifySaving(true);
    try {
      const res = await fetch("/api/preferences", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ notify_on_crash: val }),
      });
      if (!res.ok) { toast.error("Not yet wired"); }
    } catch {
      toast.error("Not yet wired");
    } finally {
      setNotifySaving(false);
    }
  };

  // ── Delete ─────────────────────────────────────────────────────────────────
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const handleDelete = () => {
    if (!deleteConfirm) {
      setDeleteConfirm(true);
      return;
    }
    // Undo flow via toast
    setDeleting(true);
    let cancelled = false;
    toast.action(`Deleting ${app.appName}…`, {
      label: "Undo",
      onClick: () => {
        cancelled = true;
        setDeleting(false);
        setDeleteConfirm(false);
        toast.info("Delete cancelled");
      },
      duration: 5 * 60 * 1000,
    });

    // After 5s if not cancelled, execute deletion
    setTimeout(async () => {
      if (cancelled) return;
      try {
        const res = await fetch(`/api/apps/${encodeURIComponent(app.appName)}`, {
          method: "DELETE",
        });
        if (!res.ok) {
          toast.error("Delete failed");
          setDeleting(false);
          return;
        }
        toast.success(`${app.appName} deleted`);
        void mutate("/api/apps");
        onClose?.();
      } catch {
        toast.error("Delete failed");
        setDeleting(false);
      }
    }, 5000);
  };

  const rowStyle: React.CSSProperties = {
    padding: "14px 16px",
    background: M.surface,
    border: `1px solid ${M.line}`,
    borderRadius: M.rMd,
    display: "flex",
    flexDirection: "column",
    gap: 10,
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 20, paddingTop: 4 }}>
      {/* ── Subdomain ── */}
      {!app.isSelfApp && (
        <section>
          <SectionHeader title="Subdomain" />
          <div style={rowStyle}>
            <p style={{ margin: 0, fontSize: 12.5, color: M.fg2, fontFamily: M.fontSans, lineHeight: 1.5 }}>
              The public URL for this app. Leave empty to use the app name.
            </p>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <input
                value={subdomain}
                onChange={(e) => {
                  setSubdomain(e.target.value);
                  setSubdomainError(null);
                }}
                placeholder={app.appName}
                style={{
                  flex: 1,
                  padding: "6px 10px",
                  borderRadius: M.rSm,
                  fontSize: 13,
                  fontFamily: M.fontMono,
                  background: M.surface2,
                  border: `1px solid ${subdomainError ? M.accentLine : M.line2}`,
                  color: M.fg,
                  outline: "none",
                }}
              />
              <span style={{ fontSize: 12, color: M.fg3, flexShrink: 0, fontFamily: M.fontMono }}>
                .{baseDomain}
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={handleSubdomainSave}
                disabled={subdomainSaving}
              >
                {subdomainSaving ? "Saving…" : "Save"}
              </Button>
            </div>
            {subdomainError && (
              <p style={{ margin: 0, fontSize: 11, color: M.err, fontFamily: M.fontSans }}>
                {subdomainError}
              </p>
            )}
          </div>
        </section>
      )}

      {/* ── Pin ── */}
      <section>
        <SectionHeader title="Version Pin" />
        <div style={rowStyle}>
          <div style={{ display: "flex", alignItems: "flex-start", gap: 12 }}>
            <div style={{ flex: 1 }}>
              <p style={{ margin: 0, fontSize: 12.5, color: M.fg2, fontFamily: M.fontSans, lineHeight: 1.5 }}>
                {isPinned
                  ? `Pinned to ${app.pinnedImage ?? "current version"}. Auto-deploy is paused.`
                  : "Auto-deploy is active. Pin to freeze at the current commit."}
              </p>
              {app.commitSha && (
                <p style={{ margin: "6px 0 0", fontSize: 11, color: M.fg3, fontFamily: M.fontMono }}>
                  Current: {app.commitSha.slice(0, 7)}
                </p>
              )}
            </div>
            <Button
              variant={isPinned ? "secondary" : "accent"}
              size="sm"
              onClick={isPinned ? handleUnpin : handlePin}
              disabled={pinning}
            >
              {pinning ? "…" : isPinned ? "Unpin" : "Pin to current commit"}
            </Button>
          </div>
        </div>
      </section>

      {/* ── Notifications ── */}
      <section>
        <SectionHeader title="Notifications" />
        <div style={rowStyle}>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, color: M.fg, fontFamily: M.fontSans }}>
                Email me on crash
              </div>
              <div style={{ fontSize: 11.5, color: M.fg3, fontFamily: M.fontSans, marginTop: 2 }}>
                Get notified when this app&apos;s container crashes.
              </div>
            </div>
            <ToggleSwitch checked={notifyOnCrash} onChange={handleNotifyToggle} />
          </div>
        </div>
      </section>

      {/* ── Danger zone ── */}
      <section>
        <SectionHeader title="Danger Zone" />
        <div
          style={{
            ...rowStyle,
            border: `1px solid rgba(252,165,165,0.22)`,
            background: M.errSoft,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 500, color: M.err, fontFamily: M.fontSans }}>
                Delete app
              </div>
              <div style={{ fontSize: 11.5, color: M.fg3, fontFamily: M.fontSans, marginTop: 2 }}>
                Stops the container and removes all configuration. Cannot be undone.
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={handleDelete}
              disabled={deleting}
              style={{
                color: M.err,
                border: `1px solid rgba(252,165,165,0.28)`,
                background: deleteConfirm ? M.errSoft : "transparent",
                flexShrink: 0,
              }}
            >
              {deleting ? "Deleting…" : deleteConfirm ? "Confirm delete" : "Delete app"}
            </Button>
          </div>
          {deleteConfirm && !deleting && (
            <p style={{ margin: 0, fontSize: 11, color: M.fg3, fontFamily: M.fontSans }}>
              Click again to confirm. A 5-minute undo window will appear in the notification.
            </p>
          )}
        </div>
      </section>
    </div>
  );
}
