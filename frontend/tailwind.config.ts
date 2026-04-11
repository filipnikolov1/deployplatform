import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: { base: "#0F172A", deep: "#020617" },
        surface: { glass: "rgba(255,255,255,0.04)" },
        border: { glass: "rgba(255,255,255,0.10)" },
        text: { primary: "#E2E8F0", secondary: "#94A3B8", tertiary: "#64748B" },
        accent: { primary: "#2D1B69", ghost: "#7C3AED", ghostLight: "#A78BFA" },
        status: {
          running: "#22C55E",
          failed: "#EF4444",
          stopped: "#F59E0B",
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
      borderRadius: { card: "14px", panel: "18px" },
      backdropBlur: { glass: "32px" },
      boxShadow: {
        glass: "0 24px 48px rgba(0,0,0,0.4)",
        focus: "0 0 0 2px #0F172A, 0 0 0 4px rgba(167,139,250,0.7)",
      },
      transitionDuration: { ui: "150ms" },
    },
  },
  plugins: [],
};

export default config;
