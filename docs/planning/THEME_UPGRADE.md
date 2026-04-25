# Vector theme upgrade

**Status:** planned, not executed
**Prerequisite:** repo renamed to Vector (or plan to rename in parallel)
**Estimated effort:** 12–15 hours of focused work, staged across ~3 evenings
**Branch strategy:** feature branch per stage, merge to `dev` between stages

This document is self-contained. When you come back to execute it, you shouldn't need to re-read the design conversation — everything needed is here.

---

## 1. Context

The current frontend (from the Launchpad era) has a well-designed token system that is barely used. Audit found:

- 84 hardcoded hex values across 28 distinct colors
- 93 hardcoded rgba values
- Three names for the same 14px radius (`xl`, `card`, `--c-radius-xl`)
- `duration-[120ms]` hardcoded in ~12 places despite `transitionDuration.ui: 120ms` existing
- An orphan color `#DDD6FE` used 10 times without being a token
- Token definitions duplicated between `globals.css` and `tailwind.config.ts` with no single source of truth

The goal of this upgrade is twofold:

1. **Drift fix** — make tokens actually work. Every color, radius, shadow, and duration in the codebase should reference a token. Change the token, the UI updates.
2. **Vector rebrand** — shift from the current near-black + purple look to the new dark/mysterious Vector aesthetic (deeper considered-dark backdrop, desaturated status colors, single restrained accent glow).

Doing these together rather than separately is deliberate. Doing the rebrand without the drift fix would produce a half-rebranded UI because 80% of colors are hardcoded. Doing the drift fix without the rebrand wastes the opportunity to make large mechanical changes all at once.

---

## 2. Decisions already made

These are locked. Don't re-derive them when you come back; just execute.

### Naming

- Rename `--c-ghost*` / `accent.ghost*` → `--c-accent-primary*` / `accent.primary*`. "Ghost" was Launchpad-era; Vector uses neutral `primary`.
- Rename `borderRadius.panel` → `borderRadius.2xl` (Tailwind convention).
- Kill `borderRadius.card` (duplicate of `xl`).
- Add `borderRadius.3xl: 28px` to capture the existing `rounded-[28px]` use.

### Source of truth

- CSS custom properties in `src/styles/tokens/*.css` are the single source of truth.
- Tailwind config references those custom properties via `var(--c-*)`. No more duplicate values in `tailwind.config.ts`.

### Status tokens

- Four tokens per status (dot, fg, bg, line). Verbose but matches the patterns already copy-pasted across the codebase, no `color-mix()` browser concerns.

### Dead tokens to delete

- `colors.bg.base: #0F172A` — unreferenced
- `colors.bg.deep: #020617` — unreferenced
- `colors.accent.primary: #2D1B69` — unreferenced (will be re-added with a different value under new naming)

### Font weights

- Leave as-is. Only `font-medium` and `font-semibold` in use, no cleanup needed.

### Body background

- Move from `#020202` (flat near-black) to `#0A0A0F` (considered dark with slight blue undertone). Easier on eyes, reads as more intentional.

### Orphan radius values

- `rounded-[5px]` (AppDetailDrawer.tsx:740) → change to `rounded-sm` (6px). Assumed typo/one-off.
- `rounded-[28px]` (UpdatingOverlayView.tsx:35) → keep the value, add `3xl: 28px` token.

---

## 3. Complete new token spec

All tokens live in `src/styles/tokens/` as CSS custom properties. Tailwind config consumes them via `var()`.

### `tokens/colors.css`

```css
:root {
  color-scheme: dark;

  /* Surfaces */
  --c-bg-body:   #0A0A0F;
  --c-surface-1: rgba(255, 255, 255, 0.030);
  --c-surface-2: rgba(255, 255, 255, 0.055);
  --c-surface-3: rgba(255, 255, 255, 0.080);
  --c-surface-glass: rgba(255, 255, 255, 0.040);

  /* Borders */
  --c-border-1: rgba(255, 255, 255, 0.07);
  --c-border-2: rgba(255, 255, 255, 0.12);
  --c-border-3: rgba(255, 255, 255, 0.18);
  --c-border-glass: rgba(255, 255, 255, 0.10);

  /* Foreground scale */
  --c-fg-0: #F5F5F7;
  --c-fg-1: #E2E2E8;
  --c-fg-2: #9C9CA8;
  --c-fg-3: #6B6B7A;

  /* Accent — Vector purple */
  --c-accent-primary: #7C3AED;
  --c-accent-deep:    #4C1D95;
  --c-accent-light:   #A78BFA;
  --c-accent-fg:      #DDD6FE;   /* formalizes the old orphan */
  --c-accent-soft:    rgba(124, 58, 237, 0.18);
  --c-accent-line:    rgba(167, 139, 250, 0.32);
  --c-accent-glow:    rgba(124, 58, 237, 0.15);

  /* Status — running */
  --c-status-running:      #3FB567;
  --c-status-running-fg:   #86EFAC;
  --c-status-running-bg:   rgba(34, 197, 94, 0.10);
  --c-status-running-line: rgba(34, 197, 94, 0.24);

  /* Status — building */
  --c-status-building:      #D97706;
  --c-status-building-fg:   #FCD34D;
  --c-status-building-bg:   rgba(245, 158, 11, 0.10);
  --c-status-building-line: rgba(245, 158, 11, 0.28);

  /* Status — failed */
  --c-status-failed:      #DC2626;
  --c-status-failed-fg:   #FCA5A5;
  --c-status-failed-bg:   rgba(239, 68, 68, 0.10);
  --c-status-failed-line: rgba(239, 68, 68, 0.28);

  /* Status — stopped / pending */
  --c-status-stopped:      #6B6B7A;
  --c-status-stopped-fg:   #CBD5E1;
  --c-status-stopped-bg:   rgba(100, 116, 139, 0.10);
  --c-status-stopped-line: rgba(100, 116, 139, 0.24);
}
```

### `tokens/radius.css`

```css
:root {
  --c-radius-sm:   6px;
  --c-radius-md:   8px;
  --c-radius-lg:   10px;
  --c-radius-xl:   14px;
  --c-radius-2xl:  18px;
  --c-radius-3xl:  28px;
  --c-radius-pill: 9999px;
}
```

### `tokens/motion.css`

```css
:root {
  --c-duration-fast: 120ms;
  --c-duration-base: 180ms;
  --c-duration-slow: 240ms;

  --c-ease-out:   cubic-bezier(0.2, 0.8, 0.2, 1);
  --c-ease-inout: cubic-bezier(0.4, 0, 0.2, 1);
}
```

### `tokens/shadows.css`

```css
:root {
  --c-shadow-card:   0 18px 40px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  --c-shadow-glass:  0 24px 48px rgba(0, 0, 0, 0.40), inset 0 1px 0 rgba(255, 255, 255, 0.08);
  --c-shadow-drawer: -24px 0 60px rgba(0, 0, 0, 0.50);
  --c-shadow-focus:  0 0 0 2px #0A0A0F, 0 0 0 4px rgba(167, 139, 250, 0.70);
  --c-shadow-glow:   0 0 24px var(--c-accent-glow);
}
```

### `tokens/typography.css`

```css
:root {
  --c-font-sans: var(--font-manrope), system-ui, sans-serif;
  --c-font-mono: var(--font-mono), ui-monospace, monospace;
  --c-font-features-sans: "cv11";
}
```

### `tokens/layout.css`

```css
:root {
  --sidebar-width: 220px;
}
```

### `tokens/index.css`

```css
@import "./colors.css";
@import "./typography.css";
@import "./radius.css";
@import "./motion.css";
@import "./shadows.css";
@import "./layout.css";
```

### Updated `tailwind.config.ts`

```ts
colors: {
  bg: {
    body: "var(--c-bg-body)",
  },
  surface: {
    glass: "var(--c-surface-glass)",
    1: "var(--c-surface-1)",
    2: "var(--c-surface-2)",
    3: "var(--c-surface-3)",
  },
  border: {
    glass: "var(--c-border-glass)",
    1: "var(--c-border-1)",
    2: "var(--c-border-2)",
    3: "var(--c-border-3)",
  },
  fg: {
    0: "var(--c-fg-0)",
    1: "var(--c-fg-1)",
    2: "var(--c-fg-2)",
    3: "var(--c-fg-3)",
  },
  text: {
    primary:   "var(--c-fg-1)",
    secondary: "var(--c-fg-2)",
    tertiary:  "var(--c-fg-3)",
  },
  accent: {
    primary: "var(--c-accent-primary)",
    deep:    "var(--c-accent-deep)",
    light:   "var(--c-accent-light)",
    fg:      "var(--c-accent-fg)",
    soft:    "var(--c-accent-soft)",
    line:    "var(--c-accent-line)",
    glow:    "var(--c-accent-glow)",
  },
  status: {
    running:       "var(--c-status-running)",
    "running-fg":  "var(--c-status-running-fg)",
    "running-bg":  "var(--c-status-running-bg)",
    "running-line":"var(--c-status-running-line)",
    building:      "var(--c-status-building)",
    "building-fg": "var(--c-status-building-fg)",
    "building-bg": "var(--c-status-building-bg)",
    "building-line":"var(--c-status-building-line)",
    failed:        "var(--c-status-failed)",
    "failed-fg":   "var(--c-status-failed-fg)",
    "failed-bg":   "var(--c-status-failed-bg)",
    "failed-line": "var(--c-status-failed-line)",
    stopped:       "var(--c-status-stopped)",
    "stopped-fg":  "var(--c-status-stopped-fg)",
    "stopped-bg":  "var(--c-status-stopped-bg)",
    "stopped-line":"var(--c-status-stopped-line)",
    pending:       "var(--c-status-stopped)",
  },
},
borderRadius: {
  sm:   "var(--c-radius-sm)",
  md:   "var(--c-radius-md)",
  lg:   "var(--c-radius-lg)",
  xl:   "var(--c-radius-xl)",
  "2xl":"var(--c-radius-2xl)",
  "3xl":"var(--c-radius-3xl)",
  pill: "var(--c-radius-pill)",
},
boxShadow: {
  card:   "var(--c-shadow-card)",
  glass:  "var(--c-shadow-glass)",
  drawer: "var(--c-shadow-drawer)",
  focus:  "var(--c-shadow-focus)",
  glow:   "var(--c-shadow-glow)",
},
transitionDuration: {
  fast: "var(--c-duration-fast)",
  base: "var(--c-duration-base)",
  slow: "var(--c-duration-slow)",
  ui:   "var(--c-duration-fast)",  // alias for backward compat during migration
},
transitionTimingFunction: {
  out:   "var(--c-ease-out)",
  inout: "var(--c-ease-inout)",
},
```

---

## 4. Mapping table — old → new

Use this as the find-and-replace reference. If a value isn't here, investigate before changing.

### Hex → token

| Old hardcoded value | New reference | Notes |
|---|---|---|
| `#020202` | `bg-body` / `var(--c-bg-body)` | value also changes to `#0A0A0F` |
| `#F8FAFC` | `fg-0` / `var(--c-fg-0)` | value changes to `#F5F5F7` |
| `#E2E8F0` | `fg-1` / `var(--c-fg-1)` | value changes to `#E2E2E8` |
| `#94A3B8` | `fg-2` / `var(--c-fg-2)` | value changes to `#9C9CA8` |
| `#64748B` | `fg-3` / `var(--c-fg-3)` OR `status-stopped` | context-dependent: text → fg-3, status dots → stopped |
| `#7C3AED` | `accent-primary` | unchanged value |
| `#A78BFA` | `accent-light` | unchanged value |
| `#DDD6FE` | `accent-fg` | formalizes orphan |
| `#22C55E` | `status-running` | value changes to `#3FB567` |
| `#86EFAC` | `status-running-fg` | unchanged value |
| `#F59E0B` | `status-building` | value changes to `#D97706` |
| `#FCD34D` | `status-building-fg` | unchanged value |
| `#FDE68A` | `status-building-fg` | consolidate to one amber-fg |
| `#EF4444` | `status-failed` | value changes to `#DC2626` |
| `#FCA5A5` | `status-failed-fg` | unchanged value |
| `#CBD5E1` | `status-stopped-fg` | unchanged value |
| `#C4B5FD` | `accent-fg` | consolidate purple text tokens |
| `#3B82F6` | keep as-is OR remove | only in Sidebar avatar gradient; decide during review |
| `#161616`, `#040408`, `#0B0B14`, `#0A0A12`, `#090909`, `#050510`, `#040404`, `#020204`, `#000000` | `bg-body` or `surface-1/2/3` | these are all near-blacks in GradientBackground / drawer bg; most should map to `bg-body` or be deleted |

### rgba → token

| Old rgba pattern | New reference |
|---|---|
| `rgba(255,255,255,0.025)` | `surface-1` |
| `rgba(255,255,255,0.045)` | `surface-2` |
| `rgba(255,255,255,0.065)` | `surface-3` |
| `rgba(255,255,255,0.04)` | `surface-glass` |
| `rgba(255,255,255,0.06)` | `border-1` |
| `rgba(255,255,255,0.10)` | `border-glass` or `border-2` (context) |
| `rgba(255,255,255,0.14)` | `border-3` |
| `rgba(124,58,237,0.18)` | `accent-soft` |
| `rgba(167,139,250,0.32)` | `accent-line` |
| `rgba(34,197,94,0.10)` | `status-running-bg` |
| `rgba(34,197,94,0.24)` | `status-running-line` |
| `rgba(245,158,11,0.10)` | `status-building-bg` |
| `rgba(245,158,11,0.28)` | `status-building-line` |
| `rgba(239,68,68,0.10)` | `status-failed-bg` |
| `rgba(239,68,68,0.28)` | `status-failed-line` |
| `rgba(100,116,139,0.10)` | `status-stopped-bg` |
| `rgba(100,116,139,0.24)` | `status-stopped-line` |
| Inline `shadow-[0_18-24_40-48px_rgba(0,0,0,0.35-0.4)...inset...]` | `shadow-card` or `shadow-glass` |

### Radius → token

| Old | New |
|---|---|
| `rounded-[5px]` | `rounded-sm` |
| `rounded-[6px]` | `rounded-sm` |
| `rounded-[8px]` | `rounded-md` |
| `rounded-[10px]` | `rounded-lg` |
| `rounded-[14px]` | `rounded-xl` |
| `rounded-[18px]` | `rounded-2xl` |
| `rounded-[28px]` | `rounded-3xl` |
| `rounded-card` | `rounded-xl` |
| `rounded-panel` | `rounded-2xl` |

### Duration → token

| Old | New |
|---|---|
| `duration-[120ms]` | `duration-fast` (or existing `duration-ui` alias) |
| `duration-[150ms]` | round to `duration-fast` (120ms) — the diff is imperceptible |
| `duration-[180ms]` | `duration-base` |
| `duration-[220ms]` | `duration-slow` (240ms close enough) |

---

## 5. Staged migration plan

Execute in order. Commit after each stage so you can bisect/revert cleanly.

### Stage 1 — Restructure tokens (mechanical, low risk)

**Goal:** move tokens to `src/styles/tokens/*.css`, make Tailwind reference them. **UI should look identical after this stage.**

Steps:
1. Create `src/styles/tokens/` with all six files from §3.
2. Update `src/styles/globals.css` to `@import "./tokens/index.css"` at the top. Remove the `:root { --c-* … }` block that's now in `tokens/colors.css` etc. Keep everything else (keyframes, body styles, utility classes).
3. Update `tailwind.config.ts` to use the `var(--c-*)` references from §3. Keep old token names that existed (even if unused elsewhere) to avoid breaking anything in this stage.
4. Run dev server, visually check dashboard matches before-state.

Commit: `refactor(theme): extract tokens to tokens/ directory, single source of truth`

### Stage 2 — Rename ghost → accent-primary

**Goal:** eliminate Launchpad-era naming. Find-and-replace across codebase.

Steps:
1. `--c-ghost` → `--c-accent-primary`
2. `--c-ghost-light` → `--c-accent-light`
3. `--c-ghost-soft` → `--c-accent-soft`
4. `--c-ghost-line` → `--c-accent-line`
5. `accent.ghost` → `accent.primary` in Tailwind classes (`bg-accent-ghost` → `bg-accent-primary`, `text-accent-ghost` → `text-accent-primary`, etc.)
6. `accent.ghostLight` → `accent.light`
7. `accent.ghostSoft` → `accent.soft`
8. `accent.ghostLine` → `accent.line`
9. Remove the old `accent.ghost*` keys from `tailwind.config.ts` once no references remain.
10. Visual regression check: accent elements look identical (color values unchanged in this stage).

Commit: `refactor(theme): rename ghost accent tokens to accent-primary`

### Stage 3 — Formalize orphan tokens and replace hardcoded values

**Goal:** the big mechanical pass. Replace every hardcoded color, radius, shadow, and duration with token references per the §4 mapping table.

Steps:
1. `#DDD6FE` → `accent-fg` (10 call sites). Before replacing, grep to confirm each usage is "accent text" and not some other purple.
2. Status color literals → status tokens. Every `#22C55E`, `#EF4444`, `#F59E0B`, `#64748B` in a status context → `status-running`, `status-failed`, `status-building`, `status-stopped`. Status tints (rgba variants) → status-bg / status-line tokens.
3. Near-black literals in `GradientBackground.tsx` and drawer backgrounds → evaluate each one. Most should become `bg-body`. If a specific shade is intentional (gradient stops), add a token for it.
4. Inline `shadow-[…]` → `shadow-card` or `shadow-glass` based on matching the inline value.
5. `rounded-[Npx]` → named radius tokens per the table.
6. `duration-[120ms]` → `duration-fast`. `duration-[150ms]` → `duration-fast`.
7. Delete dead Tailwind tokens: `bg.base`, `bg.deep`, old `accent.primary` (pre-rename).
8. Consolidate radius: remove `borderRadius.card` (→ `xl`) and `borderRadius.panel` (→ `2xl`). Add `borderRadius.3xl: 28px`.

**This stage is the bulk of the work (~6–8 hours).** Do it methodically, file by file, commit frequently within the stage.

Final commit for this stage: `refactor(theme): replace hardcoded values with token references`

### Stage 4 — Apply Vector color values

**Goal:** the actual rebrand. Now that tokens are actually used, changing values ships the new look.

Steps:
1. Update `--c-bg-body` from `#020202` to `#0A0A0F`.
2. Update `--c-fg-0` from `#F8FAFC` to `#F5F5F7`.
3. Update `--c-fg-1` from `#E2E8F0` to `#E2E2E8`.
4. Update `--c-fg-2` from `#94A3B8` to `#9C9CA8`.
5. Update `--c-fg-3` from `#64748B` to `#6B6B7A`.
6. Update status dot colors per §3 (desaturated values: `#3FB567`, `#D97706`, `#DC2626`, `#6B6B7A`).
7. Add the new `--c-accent-deep` and `--c-accent-glow` tokens.
8. Apply `shadow-glow` sparingly — primary CTAs and active crash markers only (see §6).

Commit: `feat(theme): apply Vector color palette`

### Stage 5 — Verification and polish

**Goal:** catch what the mechanical pass missed.

Steps:
1. Run the drift-check grep commands from §7 below. Zero hits = clean.
2. Visually walk through every screen: dashboard, app detail drawer, login, settings, activity, setup. Look for anything that still looks old-Launchpad.
3. Check reduced-motion still works (DevTools → Rendering → Emulate prefers-reduced-motion).
4. Check focus rings are visible and use `shadow-focus`.
5. Tag the Vector branding moments (see §6).

Commit: `chore(theme): verification pass, fix stragglers`

Merge `dev` → `main` once all five stages are in.

---

## 6. Vector-branding touches (optional polish)

After the mechanical migration, these give the theme its Vector personality. Skip if running short on time; the rebrand works without them.

1. **Single glow rule.** `shadow-glow` should appear only on the primary CTA in any given screen and on active crash markers on the Bug Time Machine timeline. If it shows up everywhere it stops meaning anything.
2. **Logo hook.** Render the "V" as a simple downward-pointing vector arrow (two thin lines meeting at a point). Use `--c-accent-primary`.
3. **Coordinate-grid easter egg.** On the login screen and empty states, add a faint (~2% opacity) purple coordinate grid in the background. Reinforces the "vector" reading of the name. Implement as an inline SVG or CSS gradient, not an image file.
4. **Chevron list markers.** Where you currently use bullet markers or default list styling, replace with thin `›` or custom chevron icons. Ties into the directional theme.
5. **No pure white.** Already enforced by `--c-fg-0: #F5F5F7`. Don't let anything reintroduce `#FFFFFF`.

---

## 7. Verification — greps that should return zero

Run from `frontend/` after Stage 5. Any hit is a drift leak.

```bash
# Hex literals in source (excluding tokens files and globals.css)
grep -rn --include='*.tsx' --include='*.ts' --include='*.css' \
  -E '#[0-9a-fA-F]{3,8}' src/ \
  | grep -v 'src/styles/tokens/' \
  | grep -v 'src/styles/globals.css'

# rgba/rgb literals (same exclusions)
grep -rn --include='*.tsx' --include='*.ts' --include='*.css' \
  -E 'rgba?\(' src/ \
  | grep -v 'src/styles/tokens/' \
  | grep -v 'src/styles/globals.css'

# Arbitrary radius
grep -rn --include='*.tsx' --include='*.ts' \
  -E 'rounded-\[' src/

# Arbitrary duration
grep -rn --include='*.tsx' --include='*.ts' \
  -E 'duration-\[' src/

# Arbitrary shadow
grep -rn --include='*.tsx' --include='*.ts' \
  -E 'shadow-\[' src/

# Leftover ghost naming
grep -rn --include='*.tsx' --include='*.ts' --include='*.css' \
  -E 'ghost[A-Z]|--c-ghost|accent-ghost' src/
```

Some hits in `globals.css` are expected (gradient definitions, keyframes). Evaluate each. If a value is genuinely bespoke and one-off, tokenize it or accept it and document the exception at the top of `globals.css`.

---

## 8. Claude Code prompts

Paste one at a time, commit between each. Do not run them all in one session.

### Prompt for Stage 1

```
I'm executing Stage 1 of a planned theme migration (Launchpad → Vector) on the frontend. 
This stage is purely structural — extract tokens into a new directory and make Tailwind 
reference them via CSS variables. The UI must look identical after this stage.

Tasks:
1. Create the directory `src/styles/tokens/` with these files, using exactly the contents 
   I'll paste below: colors.css, typography.css, radius.css, motion.css, shadows.css, 
   layout.css, index.css.
2. In `src/styles/globals.css`, add `@import "./tokens/index.css";` at the top. Remove 
   the `:root { --c-* ... }` token definitions that now live in tokens/colors.css, 
   tokens/radius.css, etc. Keep everything else in globals.css untouched (keyframes, 
   body styles, .glass-* utility classes, reduced-motion media query).
3. Update `tailwind.config.ts` to replace hardcoded values in `theme.extend` with 
   `var(--c-*)` references per the spec I'll paste. Preserve all existing token names 
   in the Tailwind config — do NOT remove `accent.ghost*`, `borderRadius.card`, or any 
   other legacy names yet. Those come out in later stages.

Do not change any component files in src/components/ or src/app/. Do not replace any 
hardcoded values. This stage is only about structure.

After you're done:
- Run `npm run build` and report any errors.
- Run the dev server and tell me how to visually verify nothing changed.

[Paste here: the six tokens/*.css file contents from THEME_UPGRADE.md §3]
[Paste here: the updated tailwind.config.ts from THEME_UPGRADE.md §3]
```

### Prompt for Stage 2

```
Stage 2 of the theme migration: rename all `ghost` accent tokens to `accent-primary` 
naming. Colors stay identical — this is pure rename.

Tasks:
1. In tokens/colors.css: rename --c-ghost → --c-accent-primary, --c-ghost-light → 
   --c-accent-light, --c-ghost-soft → --c-accent-soft, --c-ghost-line → --c-accent-line.
2. In tailwind.config.ts: rename accent.ghost → accent.primary, accent.ghostLight → 
   accent.light, accent.ghostSoft → accent.soft, accent.ghostLine → accent.line. Remove 
   the old ghost keys once replacements are in place.
3. Across src/ (all .tsx, .ts, .css): find every usage of the old names and update them. 
   Classes like `bg-accent-ghost`, `text-accent-ghost`, `border-accent-ghost`, etc. 
   become `bg-accent-primary`, etc. Also `var(--c-ghost)` references become 
   `var(--c-accent-primary)`.

After:
- Run `grep -rn -E 'ghost[A-Z]|--c-ghost|accent-ghost' src/` — should return nothing.
- Run `npm run build` and confirm it passes.
- Dev server check: accent elements (buttons, active links, status dots on running apps) 
  should look unchanged.
```

### Prompt for Stage 3

```
Stage 3 of the theme migration: the big mechanical replacement. Replace every hardcoded 
color, radius, shadow, and duration in src/ with token references. Use the mapping 
table I'll paste. This is the longest stage — work methodically, file by file.

Scope: src/components/**, src/app/**, src/lib/**. Skip src/styles/ (tokens are the 
source of truth).

For each file with drift:
1. Read the file in full.
2. Identify every hardcoded hex, rgba, rounded-[], shadow-[], duration-[].
3. Look up the mapping in the table below. If ambiguous (e.g., #64748B could be fg-3 
   OR status-stopped), decide based on context: text color → fg-3, status dot → 
   status-stopped.
4. Replace with the token reference.
5. If a hardcoded value doesn't appear in the mapping, STOP and ask me before 
   inventing a new token.

Also in this stage:
- Delete dead Tailwind tokens: bg.base, bg.deep.
- Remove borderRadius.card (replaced by xl) and borderRadius.panel (replaced by 2xl).
- Add borderRadius.3xl: "var(--c-radius-3xl)" if not already present.
- Update any component using rounded-card → rounded-xl, rounded-panel → rounded-2xl, 
  rounded-[5px] → rounded-sm, rounded-[28px] → rounded-3xl.

Work in small commits within this stage (commit every 3-5 files) so we can bisect if 
something visually breaks.

After:
- Run the drift-check greps from THEME_UPGRADE.md §7. Report any remaining hits.
- Run `npm run build`. Report errors.

[Paste here: the complete mapping table from THEME_UPGRADE.md §4]
```

### Prompt for Stage 4

```
Stage 4: apply the actual Vector color values. Tokens are already used everywhere 
from Stage 3, so this is literally changing values in tokens/colors.css and seeing 
the whole UI update.

Tasks in tokens/colors.css:
1. Change --c-bg-body from #020202 to #0A0A0F.
2. Change --c-fg-0 from #F8FAFC to #F5F5F7.
3. Change --c-fg-1 from #E2E8F0 to #E2E2E8.
4. Change --c-fg-2 from #94A3B8 to #9C9CA8.
5. Change --c-fg-3 from #64748B to #6B6B7A.
6. Change --c-status-running from #22C55E to #3FB567.
7. Change --c-status-building from #F59E0B to #D97706.
8. Change --c-status-failed from #EF4444 to #DC2626.
9. Change --c-status-stopped from #64748B to #6B6B7A.
10. Add --c-accent-deep: #4C1D95; and --c-accent-glow: rgba(124, 58, 237, 0.15); 
    if not already present.

Also in tokens/shadows.css:
11. Add --c-shadow-glow: 0 0 24px var(--c-accent-glow); if not already present.

Do not touch any component code in this stage.

After:
- Dev server visual check: body should now be slightly softer dark, not pure black.
  Status colors should be slightly more muted. Everything else should look similar.
- Run `npm run build`.
```

### Prompt for Stage 5

```
Stage 5: verification pass for the theme migration. Find leftover drift and fix it.

Tasks:
1. Run each of the grep commands from THEME_UPGRADE.md §7. Report all hits.
2. For each hit, either: (a) replace with a token reference, (b) if it's a genuine 
   one-off that should stay hardcoded, add a comment explaining why.
3. Walk through every route/screen in the app with the dev server. Report anything 
   that visually looks Launchpad-era instead of Vector. 
4. Verify focus rings are visible on interactive elements and use the shadow-focus token.
5. Test with prefers-reduced-motion enabled (DevTools → Rendering) — confirm 
   animations are disabled.

Report findings as a list. Do not auto-fix anything in this stage without confirming 
with me first.
```

---

## 9. Rollback plan

If something goes wrong mid-migration:

- **After Stage 1:** `git revert` the single commit. Safe; no component changes.
- **After Stage 2:** same — one revert.
- **After Stage 3:** this is the highest-risk stage. Because you committed in chunks within the stage, bisect to find the bad chunk and revert that one. Don't revert the whole stage; you'll lose hours of mechanical work.
- **After Stage 4:** just revert the token value changes in tokens/colors.css. Structure stays, old colors return.
- **After Stage 5:** by definition, nothing big changes here.

If the whole migration needs to roll back: `git reset --hard <pre-stage-1-sha>`. But by the end of Stage 3 the value-add is already permanent (drift eliminated); rolling back after that would be a mistake.

---

## 10. When to execute

Not now. Execute after:

1. The backend refactor planning is done (platform architecture, events table, shared module).
2. The Vector rename has been decided and committed to in the repo.
3. You have three clear evenings free with no other deadlines.

Don't execute this in a week when Bug Time Machine work is also happening. Context switches kill throughput on mechanical refactors.

---

## 11. What this unblocks

Once executed, you have a codebase where:

- Changing one CSS variable updates the whole UI
- Future services (Bug Time Machine, ML course project, iOS companion's web views if any) can import the same tokens
- The Style Dictionary migration (for cross-platform: web + iOS) becomes viable — you just generate the tokens in different formats from the same source
- "Vector Design System" becomes a real thing you can point to on your CV

Without this work, every new service inherits the drift and the system calcifies further.
