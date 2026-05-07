"use client";

/**
 * Sidebar — 220px fixed left rail.
 * Wordmark: "Vector / PLATFORM" two-line, text only (no rocket icon, no V badge).
 * Nav items with icons, active pill via motion.span layoutId="sidebar-active".
 * User menu at bottom — first-letter avatar + email, click opens a popover
 * implemented as a @radix-ui/react-dialog (react-popover not installed).
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import * as Dialog from "@radix-ui/react-dialog";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import { Icon } from "@/design/primitives/Icon";
import { useCommandPalette } from "@/hooks/useCommandPalette";

export const SIDEBAR_WIDTH = 220;

// ── nav items ─────────────────────────────────────────────────────────────────

interface NavItem {
  label: string;
  href: string;
  icon: string;
  matchFn?: (pathname: string) => boolean;
}

const NAV_ITEMS: NavItem[] = [
  { label: "Apps",         href: "/",          icon: "layout-grid" },
  { label: "Activity",     href: "/activity",  icon: "scroll-text" },
  { label: "Time Machine", href: "#tm",         icon: "rewind",
    matchFn: (p) => p.includes("/time-machine") },
  { label: "Setup",        href: "/setup",     icon: "rocket" },
];

function isItemActive(item: NavItem, pathname: string): boolean {
  if (item.matchFn) return item.matchFn(pathname);
  if (item.href === "/") return pathname === "/";
  return pathname.startsWith(item.href);
}

// ── TimeMachine href — resolves last-visited app from localStorage ────────────

function useTimeMachineHref(): string {
  const [href, setHref] = useState<string>("/");
  useEffect(() => {
    const last = localStorage.getItem("lastTimeMachineApp");
    if (last) setHref(`/apps/${encodeURIComponent(last)}/time-machine`);
  }, []);
  return href;
}

// ── UserMenu popover ──────────────────────────────────────────────────────────

function UserMenuPopover({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <AnimatePresence>
          {open && (
            <>
              {/* Invisible backdrop to close on outside click */}
              <Dialog.Overlay asChild forceMount>
                <div
                  style={{
                    position: "fixed",
                    inset: 0,
                    zIndex: 50,
                    background: "transparent",
                  }}
                />
              </Dialog.Overlay>

              {/* Menu panel */}
              <Dialog.Content asChild forceMount aria-label="User menu">
                <motion.div
                  initial={{ opacity: 0, y: 4, scale: 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 4, scale: 0.97 }}
                  transition={{ type: "spring", stiffness: 380, damping: 32 }}
                  style={{
                    position: "fixed",
                    bottom: 72,
                    left: 16,
                    width: SIDEBAR_WIDTH - 32,
                    zIndex: 51,
                    background: M.surface3,
                    border: `1px solid ${M.line2}`,
                    borderRadius: M.rLg,
                    boxShadow: "0 8px 32px rgba(0,0,0,0.5)",
                    overflow: "hidden",
                    fontFamily: M.fontSans,
                  }}
                >
                  {/* Signed in as */}
                  <div
                    style={{
                      padding: "12px 14px",
                      borderBottom: `1px solid ${M.line}`,
                    }}
                  >
                    <div style={{ fontSize: 11, color: M.fg3, marginBottom: 2 }}>
                      Signed in as
                    </div>
                    <div
                      style={{
                        fontSize: 12,
                        color: M.fg,
                        fontWeight: 500,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                    >
                      filipnikolov.dev
                    </div>
                  </div>

                  {/* Sign out */}
                  <Dialog.Close asChild>
                    <a
                      href="/api/auth/logout"
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 8,
                        padding: "10px 14px",
                        fontSize: 13,
                        color: M.fg2,
                        textDecoration: "none",
                        cursor: "pointer",
                        fontFamily: M.fontSans,
                        transition: "color 120ms",
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.color = M.fg)}
                      onMouseLeave={(e) => (e.currentTarget.style.color = M.fg2)}
                    >
                      <Icon name="log-out" size={13} />
                      Sign out
                    </a>
                  </Dialog.Close>
                </motion.div>
              </Dialog.Content>
            </>
          )}
        </AnimatePresence>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

export function Sidebar() {
  const pathname = usePathname();
  const tmHref = useTimeMachineHref();
  const { open: openPalette } = useCommandPalette();
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  return (
    <>
      <motion.aside
        initial={{ opacity: 0, x: -8 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
        style={{
          position: "fixed",
          top: 0,
          left: 0,
          bottom: 0,
          width: SIDEBAR_WIDTH,
          padding: "24px 16px",
          display: "flex",
          flexDirection: "column",
          borderRight: `1px solid ${M.line}`,
          background: M.bg,
          zIndex: 10,
        }}
      >
        {/* ── Wordmark ─────────────────────────────────────────────────────── */}
        <div
          style={{
            display: "flex",
            alignItems: "baseline",
            gap: 7,
            padding: "8px 10px",
            marginBottom: 32,
          }}
        >
          <span
            style={{
              fontFamily: M.fontSans,
              fontSize: 15,
              fontWeight: 600,
              color: M.fg,
              letterSpacing: "-0.02em",
            }}
          >
            Vector
          </span>
          <span
            style={{
              fontFamily: M.fontMono,
              fontSize: 10.5,
              fontWeight: 500,
              color: M.fg3,
              letterSpacing: "0.14em",
              textTransform: "uppercase",
            }}
          >
            platform
          </span>
        </div>

        {/* ── Search shortcut ────────────────────────────────────────────── */}
        <button
          type="button"
          onClick={openPalette}
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            width: "100%",
            padding: "7px 12px",
            marginBottom: 16,
            borderRadius: M.rMd,
            border: `1px solid ${M.line}`,
            background: M.surface,
            cursor: "pointer",
            fontFamily: M.fontSans,
            fontSize: 12,
            color: M.fg3,
            textAlign: "left",
            transition: "border-color 150ms, background 150ms",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = M.surface2;
            e.currentTarget.style.borderColor = M.line2;
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = M.surface;
            e.currentTarget.style.borderColor = M.line;
          }}
        >
          <Icon name="search" size={12} color={M.fg3} />
          <span style={{ flex: 1 }}>Search…</span>
          <kbd
            style={{
              padding: "1px 5px",
              border: `1px solid ${M.line2}`,
              borderRadius: 4,
              fontSize: 10,
              color: M.fg3,
              fontFamily: M.fontMono,
            }}
          >
            ⌘K
          </kbd>
        </button>

        {/* ── Nav ──────────────────────────────────────────────────────────── */}
        <nav
          role="navigation"
          aria-label="Primary"
          style={{ display: "flex", flexDirection: "column", gap: 2 }}
        >
          {NAV_ITEMS.map((item) => {
            const active = isItemActive(item, pathname);
            const href = item.href === "#tm" ? tmHref : item.href;

            return (
              <Link
                key={item.label}
                href={href}
                aria-current={active ? "page" : undefined}
                style={{
                  position: "relative",
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  padding: "8px 12px",
                  borderRadius: M.rMd,
                  background: "transparent",
                  border: "none",
                  cursor: "pointer",
                  fontFamily: M.fontSans,
                  fontSize: 13,
                  fontWeight: 500,
                  color: active ? M.fg : M.fg2,
                  letterSpacing: "-0.005em",
                  textDecoration: "none",
                  transition: "color 150ms",
                  outline: "none",
                }}
                onMouseEnter={(e) => {
                  if (!active) e.currentTarget.style.color = M.fg;
                }}
                onMouseLeave={(e) => {
                  if (!active) e.currentTarget.style.color = M.fg2;
                }}
              >
                {/* Animated active pill */}
                {active && (
                  <motion.span
                    layoutId="sidebar-active"
                    style={{
                      position: "absolute",
                      inset: 0,
                      borderRadius: M.rMd,
                      background: M.surface2,
                      border: `1px solid ${M.line}`,
                    }}
                    transition={{
                      type: "spring",
                      stiffness: 380,
                      damping: 32,
                    }}
                  />
                )}
                <span
                  style={{
                    position: "relative",
                    zIndex: 1,
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 10,
                  }}
                >
                  <Icon name={item.icon} size={14} />
                  {item.label}
                </span>
              </Link>
            );
          })}
        </nav>

        {/* ── User menu ─────────────────────────────────────────────────────── */}
        <div style={{ marginTop: "auto" }}>
          <button
            type="button"
            onClick={() => setUserMenuOpen((v) => !v)}
            style={{
              width: "100%",
              padding: "10px 12px",
              borderRadius: M.rMd,
              border: `1px solid ${M.line}`,
              background: M.surface,
              display: "flex",
              alignItems: "center",
              gap: 10,
              cursor: "pointer",
              textAlign: "left",
              transition: "background 150ms, border-color 150ms",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = M.surface2;
              e.currentTarget.style.borderColor = M.line2;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = M.surface;
              e.currentTarget.style.borderColor = M.line;
            }}
          >
            {/* First-letter avatar */}
            <div
              style={{
                width: 24,
                height: 24,
                borderRadius: "50%",
                background: `linear-gradient(135deg, ${M.accent}, #F0ABFC)`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 11,
                fontWeight: 700,
                color: M.bg,
                flexShrink: 0,
              }}
            >
              F
            </div>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 500,
                  color: M.fg,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
              >
                filipnikolov.dev
              </div>
            </div>
            <Icon name="chevrons-up-down" size={12} color={M.fg3} />
          </button>
        </div>
      </motion.aside>

      {/* User menu popover (outside sidebar stacking context) */}
      <UserMenuPopover open={userMenuOpen} onOpenChange={setUserMenuOpen} />
    </>
  );
}
