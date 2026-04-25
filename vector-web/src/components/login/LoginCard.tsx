"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { KeyRound, Loader2, Rocket } from "lucide-react";

export function LoginCard() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [focused, setFocused] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
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
        setError("Invalid password");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="w-full max-w-[380px] rounded-[14px] p-7"
      style={{
        background: "var(--c-surface-1)",
        border: "1px solid var(--c-border-2)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
      }}
    >
      {/* Logo row */}
      <div className="flex items-center gap-2.5 mb-5">
        <div
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
          style={{ background: "linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-light))" }}
        >
          <Rocket className="h-[18px] w-[18px] text-white" aria-hidden />
        </div>
        <div>
          <div
            className="text-base font-semibold tracking-[-0.01em]"
            style={{ color: "var(--c-fg-0)" }}
          >
            Vector
          </div>
          <div className="text-[11px]" style={{ color: "var(--c-fg-3)" }}>
            Deploy dashboard
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} className="flex flex-col gap-3.5">
        {/* Password input */}
        <div>
          <label
            htmlFor="login-pw"
            className="block text-[11px] font-semibold uppercase tracking-[0.12em] mb-1.5"
            style={{ color: "var(--c-fg-2)" }}
          >
            Password
          </label>
          <div className="relative">
            <span
              className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"
              style={{ color: "var(--c-fg-2)" }}
            >
              <KeyRound className="h-3.5 w-3.5" />
            </span>
            <input
              id="login-pw"
              type="password"
              autoComplete="current-password"
              autoFocus
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onFocus={() => setFocused(true)}
              onBlur={() => setFocused(false)}
              placeholder="••••••••"
              className="w-full pl-[34px] pr-3 py-2.5 text-sm rounded-md outline-none transition-[border-color] duration-[120ms]"
              style={{
                background: "var(--c-surface-1)",
                border: `1px solid ${focused ? "var(--c-accent-line)" : "var(--c-border-2)"}`,
                color: "var(--c-fg-1)",
                fontFamily: "inherit",
              }}
            />
          </div>
          {error && (
            <p className="mt-1.5 text-xs text-red-400">{error}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={loading}
          className="flex w-full items-center justify-center gap-2 rounded-md py-2.5 text-sm font-medium transition-[filter] duration-fast outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
          style={{
            background: "var(--c-accent-soft)",
            border: "1px solid var(--c-accent-line)",
            color: "var(--c-accent-fg)",
            cursor: loading ? "not-allowed" : "pointer",
            opacity: loading ? 0.7 : 1,
          }}
        >
          {loading && <Loader2 className="h-4 w-4 animate-spin" />}
          {loading ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </div>
  );
}
