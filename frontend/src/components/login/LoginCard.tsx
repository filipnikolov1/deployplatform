"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/primitives/GlassCard";
import { Button } from "@/components/primitives/Button";
import { Input } from "@/components/primitives/Input";
import { Rocket } from "lucide-react";

export function LoginCard() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

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
    <GlassCard
      variant="panel"
      className="w-full max-w-md border-white/[0.12] bg-black/35 p-8 shadow-[0_28px_70px_rgba(0,0,0,0.48),inset_0_1px_0_rgba(255,255,255,0.08)]"
    >
      <div className="mb-6 flex flex-col items-center gap-3 rounded-card border border-white/[0.08] bg-white/[0.03] px-5 py-6">
        <div className="rounded-2xl border border-sky-200/20 bg-sky-300/10 p-3">
          <Rocket className="h-7 w-7 text-sky-200" aria-hidden="true" />
        </div>
        <div className="glass-kicker">Secure Access</div>
        <h1 className="text-3xl font-semibold text-text-primary">Launchpad</h1>
        <p className="text-center text-sm text-slate-300/70">
          Enter the dashboard password to open the control surface.
        </p>
      </div>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={error ?? undefined}
          autoFocus
        />
        <Button type="submit" loading={loading}>
          Launch
        </Button>
      </form>
    </GlassCard>
  );
}
