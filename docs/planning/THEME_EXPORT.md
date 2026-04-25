# THEME_EXPORT — Launchpad → Vector migration audit

Scope: `frontend/` (Next.js 14 App Router). Read-only snapshot; no code was changed.

Token definitions live in two places that must stay in sync:

- `frontend/src/styles/globals.css` — CSS custom properties on `:root`
- `frontend/tailwind.config.ts` — Tailwind `theme.extend`

## 1. Design tokens currently defined

### CSS custom properties (`frontend/src/styles/globals.css`, `:root`)

```css
color-scheme: dark;

/* Surfaces */
--c-bg-body:   #020202;
--c-surface-1: rgba(255,255,255,0.025);
--c-surface-2: rgba(255,255,255,0.045);
--c-surface-3: rgba(255,255,255,0.065);

/* Borders */
--c-border-1: rgba(255,255,255,0.06);
--c-border-2: rgba(255,255,255,0.10);
--c-border-3: rgba(255,255,255,0.14);

/* Foreground scale */
--c-fg-0: #F8FAFC;
--c-fg-1: #E2E8F0;
--c-fg-2: #94A3B8;
--c-fg-3: #64748B;

/* Accent (ghost = purple) */
--c-ghost:       #7C3AED;
--c-ghost-light: #A78BFA;
--c-ghost-soft:  rgba(124,58,237,0.18);
--c-ghost-line:  rgba(167,139,250,0.32);

/* Status */
--c-running:  #22C55E;
--c-building: #F59E0B;
--c-failed:   #EF4444;
--c-stopped:  #64748B;

/* Radii */
--c-radius-sm:   6px;
--c-radius-md:   8px;
--c-radius-lg:   10px;
--c-radius-xl:   14px;
--c-radius-pill: 9999px;

/* Layout */
--sidebar-width: 220px;
```

### Tailwind tokens (`frontend/tailwind.config.ts`, `theme.extend`)

```ts
colors: {
  bg:      { base: "#0F172A", deep: "#020617", body: "#020202" },
  surface: {
    glass: "rgba(255,255,255,0.04)",
    1: "rgba(255,255,255,0.025)",
    2: "rgba(255,255,255,0.045)",
    3: "rgba(255,255,255,0.065)",
  },
  border: {
    glass: "rgba(255,255,255,0.10)",
    1: "rgba(255,255,255,0.06)",
    2: "rgba(255,255,255,0.10)",
    3: "rgba(255,255,255,0.14)",
  },
  text: { primary: "#E2E8F0", secondary: "#94A3B8", tertiary: "#64748B" },
  fg:   { 0: "#F8FAFC", 1: "#E2E8F0", 2: "#94A3B8", 3: "#64748B" },
  accent: {
    primary:    "#2D1B69",
    ghost:      "#7C3AED",
    ghostLight: "#A78BFA",
    ghostSoft:  "rgba(124,58,237,0.18)",
    ghostLine:  "rgba(167,139,250,0.32)",
  },
  status: {
    running:  "#22C55E",
    building: "#F59E0B",
    failed:   "#EF4444",
    stopped:  "#64748B",
    pending:  "#64748B",
  },
},
fontFamily: {
  sans: ["var(--font-manrope)", "system-ui", "sans-serif"],
  mono: ["var(--font-mono)", "ui-monospace", "monospace"],
},
fontSize: {
  xs:    ["12px", { lineHeight: "1.5" }],
  sm:    ["14px", { lineHeight: "1.5" }],
  base:  ["16px", { lineHeight: "1.5" }],
  lg:    ["18px", { lineHeight: "1.5" }],
  "2xl": ["24px", { lineHeight: "1.2" }],
  "3xl": ["32px", { lineHeight: "1.2" }],
},
borderRadius: {
  sm:    "6px",
  md:    "8px",
  lg:    "10px",
  card:  "14px",
  panel: "18px",
  xl:    "14px",
  pill:  "9999px",
},
backdropBlur: { glass: "32px", thin: "20px" },
boxShadow: {
  glass:  "0 24px 48px rgba(0,0,0,0.4)",
  drawer: "-24px 0 60px rgba(0,0,0,0.5)",
  focus:  "0 0 0 2px #0F172A, 0 0 0 4px rgba(167,139,250,0.7)",
},
transitionDuration: { ui: "120ms" },
width: { sidebar: "220px" },
```

### Duplication / conflicts between the two sources

- Foreground scale is defined twice: `--c-fg-0..3` (CSS) and `colors.fg.0..3` + `colors.text.*` (Tailwind). `text.primary` = `fg.1` = `--c-fg-1`. `text.secondary` = `fg.2` = `--c-fg-2`. `text.tertiary` = `fg.3` = `--c-fg-3`.
- Surface/border scales duplicated: `--c-surface-1..3` ≡ `colors.surface.1..3`; `--c-border-1..3` ≡ `colors.border.1..3`.
- Accent duplicated: `--c-ghost*` ≡ `colors.accent.ghost*`.
- Status duplicated: `--c-running|building|failed|stopped` ≡ `colors.status.*` (Tailwind also adds `status.pending` = `#64748B`, not in CSS).
- Radii: `--c-radius-sm|md|lg` (6/8/10) ≡ `borderRadius.sm|md|lg`. `--c-radius-xl: 14px` ≡ `borderRadius.xl: 14px` = `borderRadius.card: 14px` (three names for the same value). `borderRadius.panel: 18px` has no CSS-var counterpart.
- Body background colour: `--c-bg-body: #020202` ≡ `colors.bg.body`. `colors.bg.base: #0F172A` and `colors.bg.deep: #020617` have no CSS-var counterparts and do not appear to be referenced elsewhere in `src/`.
- `--sidebar-width: 220px` ≡ Tailwind `width.sidebar: 220px`.
- `colors.accent.primary: #2D1B69` has no CSS-var counterpart.

## 2. Typography

### Fonts imported

`frontend/src/app/layout.tsx`:

```tsx
import { Manrope, JetBrains_Mono } from "next/font/google";
const manrope = Manrope({ subsets: ["latin"], variable: "--font-manrope", display: "swap" });
const mono    = JetBrains_Mono({ subsets: ["latin"], variable: "--font-mono",    display: "swap" });
```

- `--font-manrope` → Tailwind `font-sans` (default body font, set on `body` in globals.css).
- `--font-mono` → Tailwind `font-mono`.

### Font weights observed in use

Grepped `font-{thin|light|normal|medium|semibold|bold|black|extrabold}` across `src/`:

- `font-medium` — widely used (body text, labels, buttons, badges)
- `font-semibold` — headings, section titles, numeric badges, uppercase eyebrows

No `thin / light / normal / bold / extrabold / black` usages found.

### Font-size scale

Tailwind `fontSize` override (see §1). Numeric scale only: `xs 12 / sm 14 / base 16 / lg 18 / 2xl 24 / 3xl 32`. **No `md`, `xl`, `4xl`+ tokens.** `sm:text-4xl` is used in `globals.css` `.glass-title` and relies on Tailwind's default `4xl` (not overridden).

Ad-hoc arbitrary sizes appear frequently with `text-[NNpx]`:

- `text-[10px]`, `text-[10.5px]`, `text-[11px]`, `text-[11.5px]`, `text-[12px]`, `text-[13px]`, `text-[15px]`, `text-[2.6rem]`

### Global typography tweaks

`frontend/src/styles/globals.css`:

```css
body {
  font-family: var(--font-manrope), system-ui, sans-serif;
  font-feature-settings: "cv11";  /* Manrope stylistic alternate */
}

.tabular-nums { font-variant-numeric: tabular-nums; }
```

`html` is `antialiased` via `layout.tsx` body className.

## 3. Hardcoded values (drift audit)

Scans run from `frontend/` over `src/**/*.{ts,tsx,css}`.

### Hex colours — 84 occurrences, 28 distinct values

Most-repeated hex literals:

```
10× #DDD6FE   (purple-200; used for accent text / icon tint in 10 components)
 8× #64748B   (= --c-fg-3 / --c-stopped — re-hardcoded)
 7× #FCA5A5   (red-300; failed-state foreground)
 7× #86EFAC   (green-300; running-state foreground)
 6× #F59E0B   (= --c-building — re-hardcoded)
 6× #EF4444   (= --c-failed — re-hardcoded)
 6× #22C55E   (= --c-running — re-hardcoded)
 4× #FCD34D   (amber-300; building-state foreground)
 4× #CBD5E1   (slate-300)
 3× #E2E8F0   (= --c-fg-1 — re-hardcoded)
 3× #C4B5FD   (violet-300)
 2× #7C3AED   (= --c-ghost — re-hardcoded)
 2× #040408   (nav/drawer bg)
 2× #020202   (= --c-bg-body — re-hardcoded)
 1× each: #FDE68A #F8FAFC #A78BFA #94A3B8 #3B82F6 #161616 #0F172A
          #0B0B14 #0A0A12 #090909 #050510 #040404 #020204 #000000
```

Files with the most hex literals (top 10):

```
18  src/styles/globals.css                         (definitions; expected)
18  src/components/detail/AppDetailDrawer.tsx
13  src/components/dashboard/SelfAppsBand.tsx
 8  src/components/dashboard/AppTable.tsx
 5  src/lib/status.ts
 5  src/components/dashboard/StatusHero.tsx
 4  src/components/shell/GradientBackground.tsx
 3  src/components/shell/Sidebar.tsx
 2  src/components/setup/SetupGuide.tsx
 2  src/components/setup/HealthChecklist.tsx
```

### Top 20 hardcoded-colour offenders by file:line (representative sample)

```
1.  src/components/detail/AppDetailDrawer.tsx:236-243   status palette inlined as #22C55E/#F59E0B/#EF4444/#64748B + text tints
2.  src/components/detail/AppDetailDrawer.tsx:281       #0A0A12 drawer bg
3.  src/components/detail/AppDetailDrawer.tsx:327,743,801 #DDD6FE accent text
4.  src/components/detail/AppDetailDrawer.tsx:456,472,482,523,553,590 status text tints (#86EFAC/#FCA5A5/#C4B5FD/#FDE68A)
5.  src/components/dashboard/SelfAppsBand.tsx:63-90     duplicate status palette + tints, both dot and text variants
6.  src/components/dashboard/SelfAppsBand.tsx:113       #DDD6FE accent text
7.  src/components/dashboard/AppTable.tsx:21-29         duplicate status palette (dot + text) inlined
8.  src/components/dashboard/AppTable.tsx:151,171       uppercase kickers — no hex, but arbitrary text sizes
9.  src/components/dashboard/StatusHero.tsx:20-24       duplicate status palette inlined
10. src/lib/status.ts:9-13                              status→hex map (#22C55E/#EF4444/#F59E0B/#64748B)
11. src/components/shell/GradientBackground.tsx:6,11    #050510 #0B0B14 #040408 #020204 — bespoke gradient stops
12. src/components/shell/Sidebar.tsx:57                 #DDD6FE active-link text
13. src/components/shell/Sidebar.tsx:93                 #7C3AED → #3B82F6 avatar gradient
14. src/components/setup/SetupGuide.tsx:26,125          #DDD6FE text
15. src/components/setup/HealthChecklist.tsx:61         #86EFAC / #FCA5A5 status pill text
16. src/components/setup/CodeBlock.tsx:34,54            #86EFAC copied indicator, #040408 code bg
17. src/components/settings/PreferencesForm.tsx:79,229  #DDD6FE, #FCA5A5
18. src/components/login/LoginCard.tsx:114              #DDD6FE link text
19. src/app/activity/page.tsx:86                        #DDD6FE active-tab text
20. src/components/activity/ActivityRow.tsx:200-201     tints via class strings (no hex, but repeats status pattern)
```

Pattern: **every status-coloured region re-inlines the same four base hex values plus a lighter tint, instead of referencing `--c-running|building|failed|stopped` or `colors.status.*`.** `#DDD6FE` is a de-facto "accent text" colour that has no token.

### rgba/rgb — 93 occurrences, no `hsl()` anywhere

Most-repeated rgba values:

```
5× rgba(255,255,255,0.06)      (= --c-border-1)
5× rgba(255,255,255,0.05)
5× rgba(239,68,68,0.10)        (failed tint)
4× rgba(34,197,94,0.24)        (running border)
4× rgba(34,197,94,0.10)        (running tint)
4× rgba(245,158,11,0.28)       (building border)
4× rgba(245,158,11,0.10)       (building tint)
4× rgba(239,68,68,0.28)        (failed border)
4× rgba(100,116,139,0.24)      (stopped border)
4× rgba(100,116,139,0.10)      (stopped tint)
3× rgba(255,255,255,0.04)      (= surface.glass)
3× rgba(0,0,0,0.35)            (shadow)
2× rgba(255,255,255,0.08)
2× rgba(255,255,255,0.045)     (= --c-surface-2)
2× rgba(0,0,0,0.4)             (= shadow.glass terminator)
2× rgba(124,58,237,0.18)       (= --c-ghost-soft)
```

Files with the most rgba literals:

```
26 src/components/detail/AppDetailDrawer.tsx
23 src/styles/globals.css            (definitions; expected)
 9 src/components/dashboard/AppTable.tsx
 8 src/components/dashboard/SelfAppsBand.tsx
 6 src/components/primitives/Button.tsx
 5 src/components/shell/UpdatingOverlayView.tsx
```

### Hardcoded border-radius (arbitrary values)

```
src/styles/globals.css:62                           border-radius: 6px; (focus ring)
src/app/activity/page.tsx:100,118                   rounded-[14px]     (= rounded-card / rounded-xl)
src/components/settings/PreferencesForm.tsx:94      rounded-[14px]
src/components/shell/GradientBackground.tsx:21,34   borderRadius: "50%"
src/components/shell/UpdatingOverlayView.tsx:35     rounded-[28px]     (no token)
src/components/setup/SetupGuide.tsx:17              rounded-[14px]
src/components/setup/CodeBlock.tsx:52               rounded-[10px]
src/components/dashboard/AppTable.tsx:159           rounded-[14px]
src/components/dashboard/SelfAppsBand.tsx:26        rounded-[10px]
src/components/dashboard/AppGrid.tsx:102,132        rounded-[10px], rounded-[14px]
src/components/dashboard/StatusHero.tsx:38          rounded-[10px]
src/components/login/LoginCard.tsx:37               rounded-[14px]
src/components/detail/AppDetailDrawer.tsx:647,679   rounded-[10px]
src/components/detail/AppDetailDrawer.tsx:740       rounded-[5px]      (no token)
```

### Hardcoded box-shadow (arbitrary values)

```
src/components/primitives/Button.tsx:14   shadow-[0_14px_30px_rgba(0,0,0,0.35),inset_0_1px_0_rgba(255,255,255,0.08)]
src/components/primitives/Button.tsx:16,18,20,22  shadow-[inset_0_1px_0_rgba(...)]  (five inline inset shadows)
src/components/primitives/GlassCard.tsx:41          [box-shadow:0_24px_48px_rgba(0,0,0,0.4),inset_0_1px_0_rgba(255,255,255,0.08)]
src/components/activity/ActivityTimeline.tsx:10   shadow-[0_18px_40px_rgba(0,0,0,0.35),inset_0_1px_0_rgba(255,255,255,0.04)]
src/components/activity/ActivityRow.tsx:188         shadow-[inset_0_1px_0_rgba(255,255,255,0.05)]
src/components/shell/UpdatingOverlayView.tsx:37,57,60  shadow-[0_0_60px_rgba(167,139,250,0.18)] + insets + green glow 0_0_14px_rgba(74,222,128,0.85)
src/components/detail/DeployHistoryList.tsx:85,89   shadow-[0_18px_40px_rgba(0,0,0,0.35)...]
src/components/detail/AppDetailDrawer.tsx:283,319   inline `boxShadow:` style objects
src/components/dashboard/AppTable.tsx:55,77         inline `boxShadow:` style objects
src/components/dashboard/SelfAppsBand.tsx:86        inline `boxShadow:`
src/components/settings/PreferencesForm.tsx:45      inline `boxShadow:`
```

`boxShadow.glass` and `boxShadow.drawer` tokens exist in `tailwind.config.ts` but most surfaces ignore them and hand-roll the same drop+inset pair (`0 18-26px 40-60px rgba(0,0,0,0.35-0.55)` + `inset 0 1px 0 rgba(255,255,255,0.04-0.08)`).

### Hardcoded transitions (arbitrary durations)

`duration-ui: 120ms` token exists; the canonical value `120ms` is still hand-written as `duration-[120ms]` in ~12 places (see `Sidebar.tsx:54`, `Button.tsx:35` uses `duration-ui`, `CodeBlock.tsx:30`, `SetupGuide.tsx:82,121`, `AppTable.tsx:44`, `PreferencesForm.tsx:75,206,226`, `activity/page.tsx:82`). `duration-[150ms]` also appears (`PreferencesForm.tsx:36,44`) — off-token.

## 4. Component primitives

Directory: `frontend/src/components/primitives/`

```
Button.tsx      — custom; 5 variants (primary, ghost-purple, ghost-red, solid-amber, icon), all hand-tuned with inline shadows/bg-black alpha classes. Uses lucide Loader2 for `loading`.
GlassCard.tsx   — custom; `div`-or-`button` card shell. Applies `rounded-card|panel`, `border-border-glass`, `bg-surface-glass`, `backdrop-blur-glass`, `shadow-glass`, plus a hardcoded `[box-shadow:…]` compounded shadow.
Input.tsx       — custom; labelled input built on the `.glass-control` component class (pill input, black/45 bg, inset highlight, focus border bump). Uppercase-tracked label.
Modal.tsx       — custom; React portal with Tab-trap, Escape-to-close, body scroll lock, fadeIn/scaleIn keyframe animations (180ms each).
Skeleton.tsx    — custom; line/block/circle variants, uses `.skeleton-shimmer` keyframe (opacity 0.4↔0.8 @ 1.5s).
StatusDot.tsx   — custom; 8×8 px dot with `.dot-glow` (pulsing `box-shadow` keyframe). Colour pulled from `statusConfig`.
Tabs.tsx        — custom; keyboard-navigable tablist with arrow keys, `accent-ghost/30` active state.
Toast.tsx       — custom; composed from `GlassCard`. Variants success/error/info/progress; progress variant renders `.lp-progress-bar` indeterminate bar.
```

**No shadcn/ui, no Radix, no Headless UI.** All primitives are fully custom. `lucide-react` supplies icons only.

## 5. Animation / motion

### Framer Motion usage

```
src/components/dashboard/AppCard.tsx:6,160-163,335
  import { motion } from "framer-motion";
  <motion.article
    layout
    whileHover={prefersReducedMotion ? undefined : { y: -4 }}
    transition={{ type: "spring", stiffness: 300, damping: 30 }}
  …
```

That is the **only** framer-motion consumer in the tree. No `AnimatePresence`, no `useAnimation`, no variants. Layout animations + a hover lift on `AppCard`.

### CSS keyframes (`frontend/src/styles/globals.css`)

```
skeleton-shimmer    opacity 0.4 ↔ 0.8, 1.5s ease-in-out infinite
fadeIn              opacity 0 → 1 (used by Modal via motion-safe:animate-[fadeIn_180ms_ease-out])
scaleIn             scale(0.98)+opacity 0 → scale(1)+opacity 1 (Modal content enter)
dot-glow            box-shadow pulse (4px → 10px currentColor), 2s ease-in-out infinite
lp-fadein           opacity 0 → 1, 160ms ease-out
lp-slidein          translateX(100%) → 0, 220ms cubic-bezier(0.2,0.8,0.2,1)
lp-spin             rotate 360deg (inline use: 1s linear infinite in AppDetailDrawer.tsx:831)
lp-progress-indeterminate  translateX(-40%) → 240%, 1.4s cubic-bezier(0.4,0,0.2,1) infinite
```

### Easing / duration inventory

```
Tailwind token:   transitionDuration.ui = 120ms          (used explicitly as `duration-ui` in Button.tsx only)
Arbitrary:        duration-[120ms] (~12 call sites — same value, bypasses token)
                  duration-[150ms] (PreferencesForm toggles)
                  180ms ease-out   (Modal fadeIn/scaleIn)
                  160ms ease-out   (lp-fadein)
                  220ms cubic-bezier(0.2,0.8,0.2,1)   (lp-slidein)
                  1.4s  cubic-bezier(0.4,0,0.2,1)     (lp-progress-indeterminate)
                  1.5s  ease-in-out infinite          (skeleton-shimmer)
                  2s    ease-in-out infinite          (dot-glow)
                  1s    linear infinite               (lp-spin)
Spring:           stiffness 300 / damping 30           (AppCard hover)
```

Global respect for reduced-motion (`globals.css:217-223`):

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
  }
}
```

## 6. Key screens

Skipped per user request — no screenshots captured. No `theme-export-screenshots/` directory created.

## 7. Current dark/light mode setup

- **Single dark theme, no toggle.** `globals.css:6` declares `color-scheme: dark;`. No `next-themes`, no `ThemeProvider`, no `useTheme` anywhere in `src/`.
- **No `dark:` or `light:` Tailwind variants used in the codebase** (grep returned zero hits).
- Tokens are defined once; there is no second palette for a light variant. The `--c-*` custom properties exist only on `:root` — no `.dark { … }` or `[data-theme] { … }` blocks.
- User-facing "reduced motion" preference is the only theme-like toggle (`PreferencesForm.tsx`).

## 8. Styling-relevant dependencies

From `frontend/package.json` + `node_modules/*/package.json` (installed versions):

```
tailwindcss       3.4.19   (devDep, config: frontend/tailwind.config.ts)
postcss           8.x      (devDep; frontend/postcss.config.mjs)
framer-motion     12.38.0  (runtime; single consumer in AppCard.tsx)
lucide-react      0.378.0  (runtime; icons)
next              14.2.35  (includes styled-jsx transitively; not used directly)
react / react-dom 18.x
```

Not present: `clsx`, `cva` / `class-variance-authority`, `tailwind-merge`, `@radix-ui/*`, shadcn/ui, `@emotion/*`, `styled-components`, `next-themes`.
