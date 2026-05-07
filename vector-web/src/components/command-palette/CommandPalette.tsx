"use client";

/**
 * CommandPalette — Cmd+K overlay.
 * Uses cmdk + @radix-ui/react-dialog.
 *
 * Sections:
 *  - Apps        — jump to app drawer via /?app={name}
 *  - Recent events (last 50 from useEvents)
 *  - Commits     — commit metadata from events (no per-app cache hook; renders hint if none)
 *
 * Keyboard: ↑↓ handled by cmdk, Enter navigates + closes, Esc closes.
 */

import { useEffect, useRef } from "react";
import { Command } from "cmdk";
import * as Dialog from "@radix-ui/react-dialog";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { formatDistanceToNow } from "date-fns";

import { M, MSTATUS } from "@/design/tokens";
import { Icon } from "@/design/primitives/Icon";
import { useCommandPalette } from "@/hooks/useCommandPalette";
import { useApps } from "@/hooks/useApps";
import { useEvents } from "@/hooks/useEvents";
import type { DeploymentStatus } from "@/types/deployment";

// ── helpers ──────────────────────────────────────────────────────────────────

function statusDot(status: DeploymentStatus): string {
  if (status === "RUNNING") return MSTATUS.RUNNING.dot;
  if (status === "FAILED") return MSTATUS.FAILED.dot;
  if (status === "STOPPED") return MSTATUS.STOPPED.dot;
  if (status === "PENDING") return MSTATUS.PENDING.dot;
  return M.fg3;
}

function shortSha(sha: string | null | undefined): string {
  return sha ? sha.slice(0, 7) : "";
}

// ── sub-components ────────────────────────────────────────────────────────────

function SectionHeader({ children }: { children: React.ReactNode }) {
  return (
    <Command.Group
      heading={children as string}
      style={{
        "--cmdk-separator-opacity": "0",
      } as React.CSSProperties}
    />
  );
}

// ── main component ────────────────────────────────────────────────────────────

export function CommandPalette() {
  const { isOpen, close } = useCommandPalette();
  const router = useRouter();
  const inputRef = useRef<HTMLInputElement>(null);

  const { apps } = useApps();
  const { events } = useEvents({ limit: 50 });

  // Focus input when palette opens
  useEffect(() => {
    if (isOpen) {
      // Small delay so the dialog animation has started
      const t = setTimeout(() => inputRef.current?.focus(), 60);
      return () => clearTimeout(t);
    }
  }, [isOpen]);

  function navigate(href: string) {
    close();
    router.push(href);
  }

  // Deduplicate commits from events (by commitSha)
  const commitEvents = events
    .filter((e) => e.commitSha && e.commitMessage)
    .reduce<typeof events>((acc, e) => {
      if (!acc.find((x) => x.commitSha === e.commitSha)) acc.push(e);
      return acc;
    }, [])
    .slice(0, 50);

  // Recent events (all, last 50)
  const recentEvents = events.slice(0, 50);

  return (
    <Dialog.Root open={isOpen} onOpenChange={(o) => { if (!o) close(); }}>
      <Dialog.Portal>
        <AnimatePresence>
          {isOpen && (
            <>
              {/* Backdrop */}
              <Dialog.Overlay asChild forceMount>
                <motion.div
                  key="cmd-backdrop"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.18 }}
                  style={{
                    position: "fixed",
                    inset: 0,
                    zIndex: 80,
                    background: "rgba(8,8,10,0.72)",
                    backdropFilter: "blur(6px)",
                    WebkitBackdropFilter: "blur(6px)",
                  }}
                />
              </Dialog.Overlay>

              {/* Panel */}
              <Dialog.Content asChild forceMount aria-label="Command palette">
                <motion.div
                  key="cmd-panel"
                  initial={{ opacity: 0, y: -12, scale: 0.97 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -8, scale: 0.97 }}
                  transition={{ type: "spring", stiffness: 320, damping: 28 }}
                  style={{
                    position: "fixed",
                    left: "50%",
                    top: "12%",
                    transform: "translateX(-50%)",
                    width: "min(620px, 92vw)",
                    zIndex: 81,
                    background: M.surface2,
                    border: `1px solid ${M.line2}`,
                    borderRadius: M.rXl,
                    boxShadow: "0 32px 80px rgba(0,0,0,0.6)",
                    overflow: "hidden",
                    fontFamily: M.fontSans,
                  }}
                >
                  <Command
                    label="Command palette"
                    style={{ background: "transparent" }}
                    loop
                  >
                    {/* Search row */}
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 10,
                        padding: "14px 16px",
                        borderBottom: `1px solid ${M.line}`,
                      }}
                    >
                      <Icon name="search" size={14} color={M.fg3} />
                      <Command.Input
                        ref={inputRef}
                        placeholder="Jump to app, command, or commit…"
                        style={{
                          flex: 1,
                          background: "transparent",
                          border: "none",
                          outline: "none",
                          color: M.fg,
                          fontFamily: M.fontSans,
                          fontSize: 14,
                          letterSpacing: "-0.005em",
                        }}
                      />
                      <kbd
                        style={{
                          padding: "2px 6px",
                          border: `1px solid ${M.line2}`,
                          borderRadius: 4,
                          fontSize: 10,
                          color: M.fg3,
                          fontFamily: M.fontMono,
                        }}
                      >
                        esc
                      </kbd>
                    </div>

                    {/* Results list */}
                    <Command.List
                      style={{
                        maxHeight: 420,
                        overflow: "auto",
                        padding: "8px 0",
                      }}
                    >
                      <Command.Empty
                        style={{
                          padding: "32px 20px",
                          textAlign: "center",
                          color: M.fg3,
                          fontSize: 13,
                          fontFamily: M.fontSans,
                        }}
                      >
                        No matches found.
                      </Command.Empty>

                      {/* ── Navigate section ───────────────────────────────── */}
                      <Command.Group>
                        <GroupLabel>Navigate</GroupLabel>
                        {[
                          { id: "apps",        label: "Go to Apps",         icon: "layout-grid",  href: "/" },
                          { id: "activity",    label: "Go to Activity",     icon: "scroll-text",  href: "/activity" },
                          { id: "timemachine", label: "Go to Time Machine", icon: "rewind",       href: "/" },
                          { id: "setup",       label: "Go to Setup",        icon: "rocket",       href: "/setup" },
                        ].map((it) => (
                          <PaletteItem
                            key={it.id}
                            value={it.label}
                            icon={it.icon}
                            label={it.label}
                            onSelect={() => navigate(it.href)}
                          />
                        ))}
                      </Command.Group>

                      {/* ── Apps section ──────────────────────────────────── */}
                      {apps.length > 0 && (
                        <Command.Group>
                          <GroupLabel>Apps</GroupLabel>
                          {apps.map((app) => (
                            <PaletteItem
                              key={`app:${app.appName}`}
                              value={`${app.appName} ${app.imageName} ${app.containerPort}`}
                              icon="box"
                              label={app.appName}
                              sub={`${app.imageName} · :${app.containerPort}`}
                              leadingDot={statusDot(app.status)}
                              onSelect={() => navigate(`/?app=${encodeURIComponent(app.appName)}`)}
                            />
                          ))}
                        </Command.Group>
                      )}

                      {/* ── Recent events section ─────────────────────────── */}
                      {recentEvents.length > 0 && (
                        <Command.Group>
                          <GroupLabel>Recent events</GroupLabel>
                          {recentEvents.map((e, i) => {
                            const label = e.commitMessage ?? e.eventType;
                            const sub = [
                              e.appName,
                              shortSha(e.commitSha),
                              formatDistanceToNow(new Date(e.createdAt), { addSuffix: true }),
                            ]
                              .filter(Boolean)
                              .join(" · ");
                            return (
                              <PaletteItem
                                key={`evt:${e.id ?? i}`}
                                value={`${label} ${e.appName} ${e.commitSha ?? ""}`}
                                icon="git-commit-horizontal"
                                label={label}
                                sub={sub}
                                onSelect={() => navigate(`/activity`)}
                              />
                            );
                          })}
                        </Command.Group>
                      )}

                      {/* ── Commits section ───────────────────────────────── */}
                      <Command.Group>
                        <GroupLabel>Commits</GroupLabel>
                        {commitEvents.length === 0 ? (
                          <div
                            style={{
                              padding: "8px 16px 12px",
                              fontSize: 12,
                              color: M.fg3,
                              fontFamily: M.fontMono,
                            }}
                          >
                            No commits cached yet.
                          </div>
                        ) : (
                          commitEvents.map((e, i) => {
                            const sha = shortSha(e.commitSha);
                            const label = e.commitMessage ?? sha;
                            const sub = `${e.appName} · ${sha} · ${formatDistanceToNow(new Date(e.createdAt), { addSuffix: true })}`;
                            return (
                              <PaletteItem
                                key={`commit:${e.commitSha ?? i}`}
                                value={`${label} ${sha} ${e.appName}`}
                                icon="git-branch"
                                label={label}
                                sub={sub}
                                onSelect={() =>
                                  navigate(`/?app=${encodeURIComponent(e.appName)}`)
                                }
                              />
                            );
                          })
                        )}
                      </Command.Group>
                    </Command.List>
                  </Command>
                </motion.div>
              </Dialog.Content>
            </>
          )}
        </AnimatePresence>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

// ── small helpers ─────────────────────────────────────────────────────────────

function GroupLabel({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        padding: "10px 16px 4px",
        fontSize: 10,
        fontWeight: 600,
        color: M.fg3,
        letterSpacing: "0.16em",
        textTransform: "uppercase",
        fontFamily: M.fontSans,
      }}
    >
      {children}
    </div>
  );
}

interface PaletteItemProps {
  value: string;
  icon: string;
  label: string;
  sub?: string;
  leadingDot?: string;
  onSelect: () => void;
}

function PaletteItem({ value, icon, label, sub, leadingDot, onSelect }: PaletteItemProps) {
  return (
    <Command.Item
      value={value}
      onSelect={onSelect}
      style={{
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: "9px 16px",
        cursor: "pointer",
        outline: "none",
        fontFamily: M.fontSans,
      }}
      // cmdk adds data-selected; we use CSS for the highlight
    >
      {leadingDot && (
        <span
          aria-hidden
          style={{
            width: 6,
            height: 6,
            borderRadius: "50%",
            background: leadingDot,
            flexShrink: 0,
          }}
        />
      )}
      <Icon name={icon} size={14} color={M.fg2} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: 13.5,
            color: M.fg,
            fontWeight: 500,
            letterSpacing: "-0.005em",
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
        >
          {label}
        </div>
        {sub && (
          <div
            style={{
              fontSize: 11.5,
              color: M.fg3,
              fontFamily: M.fontMono,
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
              marginTop: 2,
            }}
          >
            {sub}
          </div>
        )}
      </div>
    </Command.Item>
  );
}
