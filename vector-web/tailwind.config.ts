import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: { base: "#0F172A", deep: "#020617", body: "#020202" },
        // Clean-variant surfaces (flatter glass, still translucent)
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
        // fg0–fg3 mirrors CleanTokens for inline usage
        fg: {
          0: "#F8FAFC",
          1: "#E2E8F0",
          2: "#94A3B8",
          3: "#64748B",
        },
        accent: {
          primary: "#2D1B69",
          ghost: "#7C3AED",
          ghostLight: "#A78BFA",
          ghostSoft: "rgba(124,58,237,0.18)",
          ghostLine: "rgba(167,139,250,0.32)",
        },
        status: {
          running: "#22C55E",
          building: "#F59E0B",
          failed: "#EF4444",
          stopped: "#64748B",
          pending: "#64748B",
        },
      },
      fontFamily: {
        sans: ["var(--font-manrope)", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "ui-monospace", "monospace"],
      },
      fontSize: {
        xs: ["12px", { lineHeight: "1.5" }],
        sm: ["14px", { lineHeight: "1.5" }],
        base: ["16px", { lineHeight: "1.5" }],
        lg: ["18px", { lineHeight: "1.5" }],
        "2xl": ["24px", { lineHeight: "1.2" }],
        "3xl": ["32px", { lineHeight: "1.2" }],
      },
      borderRadius: {
        sm: "6px",
        md: "8px",
        lg: "10px",
        card: "14px",
        panel: "18px",
        xl: "14px",
        pill: "9999px",
      },
      backdropBlur: { glass: "32px", thin: "20px" },
      boxShadow: {
        glass: "0 24px 48px rgba(0,0,0,0.4)",
        drawer: "-24px 0 60px rgba(0,0,0,0.5)",
        focus: "0 0 0 2px #0F172A, 0 0 0 4px rgba(167,139,250,0.7)",
      },
      transitionDuration: { ui: "120ms" },
      width: { sidebar: "220px" },
    },
  },
  plugins: [],
};

export default config;
