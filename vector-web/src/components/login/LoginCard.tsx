"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { Loader2 } from "lucide-react";
import { M } from "@/design/tokens";
import { Button } from "@/design/primitives";
import { toast } from "@/lib/toast";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";

export function LoginCard() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [focused, setFocused] = useState(false);
  const reduced = usePrefersReducedMotion();

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ password }),
      });
      if (res.ok) {
        router.push("/");
        router.refresh();
      } else {
        toast.error("Invalid password");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <motion.form
      onSubmit={onSubmit}
      initial={reduced ? false : { opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.32, ease: [0.22, 1, 0.36, 1] }}
      style={{
        width: "100%",
        maxWidth: 400,
        padding: "40px 32px",
        background: M.surface,
        border: `1px solid ${M.line}`,
        borderRadius: M.rXl,
        boxShadow: "0 24px 48px rgba(0,0,0,0.4)",
        fontFamily: M.fontSans,
      }}
    >
      {/* Wordmark */}
      <motion.div
        initial={reduced ? false : { opacity: 0, y: -6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.32, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
        style={{
          display: "flex",
          alignItems: "baseline",
          justifyContent: "center",
          gap: 9,
          marginBottom: 28,
        }}
      >
        <span
          style={{
            fontFamily: M.fontSans,
            fontSize: 22,
            fontWeight: 600,
            color: M.fg,
            letterSpacing: "-0.025em",
          }}
        >
          Vector
        </span>
        <span
          style={{
            fontFamily: M.fontMono,
            fontSize: 12,
            fontWeight: 500,
            color: M.fg3,
            letterSpacing: "0.16em",
            textTransform: "uppercase",
          }}
        >
          Platform
        </span>
      </motion.div>

      {/* Heading */}
      <h1
        style={{
          fontFamily: M.fontSans,
          fontSize: 20,
          fontWeight: 600,
          color: M.fg,
          margin: "0 0 6px",
          letterSpacing: "-0.02em",
          textAlign: "center",
        }}
      >
        Welcome back
      </h1>
      <p
        style={{
          fontSize: 13.5,
          color: M.fg2,
          margin: "0 0 32px",
          textAlign: "center",
        }}
      >
        Sign in to your workspace.
      </p>

      {/* Fields */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 14,
          marginBottom: 24,
        }}
      >
        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <span
            style={{
              fontSize: 11.5,
              fontWeight: 600,
              color: M.fg3,
              letterSpacing: "0.10em",
              textTransform: "uppercase",
            }}
          >
            Password
          </span>
          <input
            id="login-pw"
            type="password"
            autoComplete="current-password"
            autoFocus
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            placeholder="••••••••"
            style={{
              width: "100%",
              padding: "10px 14px",
              borderRadius: M.rMd,
              background: M.bg,
              border: `1px solid ${focused ? M.line3 : M.line2}`,
              color: M.fg,
              fontFamily: M.fontSans,
              fontSize: 14,
              outline: "none",
              transition: "border-color 150ms",
              boxSizing: "border-box",
            }}
          />
        </label>
      </div>

      {/* Submit */}
      <Button
        variant="primary"
        type="submit"
        disabled={loading}
        style={{ width: "100%", justifyContent: "center" }}
      >
        {loading && (
          <Loader2 className="animate-spin" style={{ width: 14, height: 14 }} />
        )}
        {loading ? "Signing in…" : "Sign in"}
      </Button>
    </motion.form>
  );
}
