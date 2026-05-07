/**
 * Vector Platform — Design Tokens
 * Source of truth: ported from MTokens.jsx in the design handoff.
 * CSS mirror: src/design/colors_and_type.css
 */

export const M = {
  // Surfaces — flat, four steps, no glass
  bg: "#08080A",
  surface: "#0E0E12",
  surface2: "#15151B",
  surface3: "#1C1C24",

  // Borders — barely-there hairlines
  line: "rgba(255,255,255,0.06)",
  line2: "rgba(255,255,255,0.10)",
  line3: "rgba(255,255,255,0.16)",

  // Foreground — 4-step ramp
  fg: "#F5F5F7",
  fg2: "#9090A0",
  fg3: "#5A5A68",
  fg4: "#3A3A44",

  // Single accent — violet, used sparingly
  accent: "#A78BFA",
  accentLight: "#DDD6FE",
  accentSoft: "rgba(167,139,250,0.10)",
  accentLine: "rgba(167,139,250,0.28)",

  // Status — pastel, never saturated primaries
  ok: "#86EFAC",
  okSoft: "rgba(134,239,172,0.10)",
  warn: "#FCD34D",
  warnSoft: "rgba(252,211,77,0.10)",
  err: "#FCA5A5",
  errSoft: "rgba(252,165,165,0.10)",

  // Typography
  fontSans: "'Geist', 'Inter', -apple-system, system-ui, sans-serif",
  fontMono: "'JetBrains Mono', ui-monospace, monospace",

  // Radii
  rSm: 6,
  rMd: 10,
  rLg: 14,
  rXl: 20,
  rPill: 999,
} as const;

export type MTokenKey = keyof typeof M;

// Status palette — per-status dot/text/bg/line/label
export const MSTATUS = {
  RUNNING: {
    dot: M.ok,
    text: M.ok,
    bg: M.okSoft,
    line: "rgba(134,239,172,0.22)",
    label: "Running",
  },
  BUILDING: {
    dot: M.warn,
    text: M.warn,
    bg: M.warnSoft,
    line: "rgba(252,211,77,0.22)",
    label: "Building",
  },
  PENDING: {
    dot: M.fg3,
    text: M.fg2,
    bg: "transparent",
    line: M.line2,
    label: "Pending",
  },
  STOPPED: {
    dot: M.fg3,
    text: M.fg2,
    bg: "transparent",
    line: M.line2,
    label: "Stopped",
  },
  FAILED: {
    dot: M.err,
    text: M.err,
    bg: M.errSoft,
    line: "rgba(252,165,165,0.22)",
    label: "Failed",
  },
  CRASHED: {
    dot: M.err,
    text: M.err,
    bg: M.errSoft,
    line: "rgba(252,165,165,0.22)",
    label: "Crashed",
  },
} as const;

export type MStatus = keyof typeof MSTATUS;

// Motion presets — use as framer-motion variants
export const MMOTION = {
  // Page enter/exit
  page: {
    initial: { opacity: 0, y: 8 },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: -4 },
    transition: { duration: 0.32, ease: [0.22, 1, 0.36, 1] as const },
  },
  // Stagger container for lists
  list: {
    initial: { opacity: 0 },
    animate: {
      opacity: 1,
      transition: { staggerChildren: 0.04, delayChildren: 0.05 },
    },
  },
  // List item
  item: {
    initial: { opacity: 0, y: 8 },
    animate: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.32, ease: [0.22, 1, 0.36, 1] as const },
    },
  },
  // Spring drawer (from right)
  drawer: {
    initial: { x: "100%", opacity: 0.5 },
    animate: {
      x: 0,
      opacity: 1,
      transition: { type: "spring", stiffness: 260, damping: 32 } as const,
    },
    exit: {
      x: "100%",
      opacity: 0,
      transition: { duration: 0.22, ease: [0.4, 0, 1, 1] as const },
    },
  },
  // Modal pop
  modal: {
    initial: { opacity: 0, scale: 0.96, y: 12 },
    animate: {
      opacity: 1,
      scale: 1,
      y: 0,
      transition: { type: "spring", stiffness: 320, damping: 30 } as const,
    },
    exit: {
      opacity: 0,
      scale: 0.97,
      y: 8,
      transition: { duration: 0.18, ease: [0.4, 0, 1, 1] as const },
    },
  },
  // Backdrop
  backdrop: {
    initial: { opacity: 0 },
    animate: { opacity: 1, transition: { duration: 0.22 } },
    exit: { opacity: 0, transition: { duration: 0.18 } },
  },
  // Spring spring presets (for use in transition prop)
  spring: {
    snap: { type: "spring" as const, stiffness: 380, damping: 32 },
    drawer: { type: "spring" as const, stiffness: 260, damping: 32 },
    soft: { type: "spring" as const, stiffness: 300, damping: 24 },
  },
} as const;
