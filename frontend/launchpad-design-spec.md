# Launchpad Design Specification: Liquid Glass Developer Tool

## 1. Vision & Core Aesthetic

**The "Liquid Glass" Aesthetic**

Launchpad is designed to feel like an "Ethereal Command Center." It moves away from the rigid, utilitarian look of traditional developer tools in favor of a modern, editorial, and highly visual interface.

- **Creative North Star:** A premium, high-tech interface that balances depth, transparency, and clarity.
- **Visual Language:** Translucent frosted glass, subtle flowing background blurs, and organic pill shapes.

## 2. Design Principles

- **Depth through Layering:** Uses stacking order (z-index) and glass properties (backdrop-blur, opacity) to create a sense of physical space.
- **Soft Geometry:** Every container, button, and input uses maximum roundness (pill shapes) to soften the "tech" feel and create a cohesive design system.
- **Clarity through Contrast:** While the theme is dark, critical information (status dots, metrics) and primary actions (deep purple buttons) use high contrast to ensure immediate legibility.

## 3. Visual Tokens

### Color Palette

| Role | Value |
|---|---|
| Background | Deep Slate/Navy `#0F172A` → `#020617` with flowing blue/purple gradient blurs |
| Primary Action | Solid Dark Purple `#2D1B69` with White `#FFFFFF` text |
| Surfaces | Frosted Glass (White/Slate at 5–10% opacity) with `backdrop-blur: 32px` |
| Status — Success | Green (Uptime / Success) |
| Status — Alert | Red (Issue / Alert) |
| Status — Warning | Amber (Warning / Degraded) |

### Typography

| Role | Font |
|---|---|
| Primary | Manrope — clean, modern sans-serif |
| Monospace | Fira Code or JetBrains Mono — for logs and terminal data |

### Effects

- **Border:** 1px subtle white/purple border at low opacity (10%) to define glass edges.
- **Shadows:** Large, soft drop shadows — `0px 24px 48px rgba(0,0,0,0.4)` — to elevate glass panels.

## 4. Key User Flows & Screens

### A. Launchpad Dashboard (Desktop & Mobile)

The main hub for server management.

- **Applications List:** A vertical stack of frosted glass pill cards.
- **Card Data:** Status dot, App Name, Image Tag, and Uptime Percentage.
- **Layout:** Desktop uses a sidebar for navigation and high-level insights; Mobile uses a sticky bottom navigation bar.

### B. App Details Modal (Interactive Management)

A deep-dive view triggered by clicking an application card.

- **App Details Panel:** Contains environment variables (censored for security), deployment status, and the primary "Redeploy" action.
- **Real-time Build Logs:** A dedicated glass container with monospaced text streaming.
- **AI Integration:** A prominent "Ask AI: Analyze Logs" button directly integrated with the log stream to provide instant troubleshooting context.

## 5. Development Implementation Notes

- **Framework:** Built with HTML5 and Tailwind CSS.
- **Glass Effect:** Use Tailwind's `backdrop-blur-{size}`, `bg-white/5`, and `border-white/10`.
- **Responsive Strategy:** Desktop uses a flexible grid/sidebar layout; Mobile transitions to a full-screen stacked view with simplified navigation.
- **Components:** Leverage the `TopNavBar`, `SideNavBar`, and `BottomNavBar` shared components for consistency across all views.
