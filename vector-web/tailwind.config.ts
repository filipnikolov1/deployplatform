import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: {
          base: "#0F172A",
          deep: "#020617",
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
        text: {
          primary:   "var(--c-fg-1)",
          secondary: "var(--c-fg-2)",
          tertiary:  "var(--c-fg-3)",
        },
        fg: {
          0: "var(--c-fg-0)",
          1: "var(--c-fg-1)",
          2: "var(--c-fg-2)",
          3: "var(--c-fg-3)",
        },
        accent: {
          primary:    "var(--c-accent-primary)",
          deep:       "var(--c-accent-deep)",
          light:      "var(--c-accent-light)",
          fg:         "var(--c-accent-fg)",
          soft:       "var(--c-accent-soft)",
          line:       "var(--c-accent-line)",
          glow:       "var(--c-accent-glow)",
        },
        status: {
          running:        "var(--c-status-running)",
          "running-fg":   "var(--c-status-running-fg)",
          "running-bg":   "var(--c-status-running-bg)",
          "running-line": "var(--c-status-running-line)",
          building:       "var(--c-status-building)",
          "building-fg":  "var(--c-status-building-fg)",
          "building-bg":  "var(--c-status-building-bg)",
          "building-line":"var(--c-status-building-line)",
          failed:         "var(--c-status-failed)",
          "failed-fg":    "var(--c-status-failed-fg)",
          "failed-bg":    "var(--c-status-failed-bg)",
          "failed-line":  "var(--c-status-failed-line)",
          stopped:        "var(--c-status-stopped)",
          "stopped-fg":   "var(--c-status-stopped-fg)",
          "stopped-bg":   "var(--c-status-stopped-bg)",
          "stopped-line": "var(--c-status-stopped-line)",
          pending:        "var(--c-status-stopped)",
        },
      },
      fontFamily: {
        sans: ["var(--font-manrope)", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "ui-monospace", "monospace"],
      },
      fontSize: {
        xs:   ["12px", { lineHeight: "1.5" }],
        sm:   ["14px", { lineHeight: "1.5" }],
        base: ["16px", { lineHeight: "1.5" }],
        lg:   ["18px", { lineHeight: "1.5" }],
        "2xl":["24px", { lineHeight: "1.2" }],
        "3xl":["32px", { lineHeight: "1.2" }],
      },
      borderRadius: {
        sm:    "var(--c-radius-sm)",
        md:    "var(--c-radius-md)",
        lg:    "var(--c-radius-lg)",
        xl:    "var(--c-radius-xl)",
        "2xl": "var(--c-radius-2xl)",
        "3xl": "var(--c-radius-3xl)",
        pill:  "var(--c-radius-pill)",
        card:  "var(--c-radius-xl)",
        panel: "var(--c-radius-2xl)",
      },
      backdropBlur: { glass: "32px", thin: "20px" },
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
        ui:   "var(--c-duration-fast)",
      },
      transitionTimingFunction: {
        out:   "var(--c-ease-out)",
        inout: "var(--c-ease-inout)",
      },
      width: { sidebar: "220px" },
    },
  },
  plugins: [],
};

export default config;
