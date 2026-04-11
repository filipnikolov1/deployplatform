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
    <GlassCard variant="panel" className="w-full max-w-sm p-8 flex flex-col gap-6">
      <div className="flex flex-col items-center gap-2">
        <Rocket className="h-8 w-8 text-accent-ghostLight" aria-hidden="true" />
        <h1 className="text-2xl font-semibold text-text-primary">Launchpad</h1>
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
