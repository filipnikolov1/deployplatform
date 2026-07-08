"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/design/primitives";
import { M } from "@/design/tokens";

interface ExchangeResponse {
  slug: string;
  ownerLogin: string;
}

function GitHubAppCallbackContent() {
  const searchParams = useSearchParams();
  const code = searchParams.get("code");
  const [result, setResult] = useState<ExchangeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!code) {
      setError("Missing code from GitHub.");
      return;
    }
    let cancelled = false;
    fetch("/api/github/app-manifest/exchange", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code }),
    })
      .then((res) => {
        if (!res.ok) throw new Error("Exchange failed");
        return res.json();
      })
      .then((data: ExchangeResponse) => {
        if (!cancelled) setResult(data);
      })
      .catch(() => {
        if (!cancelled) setError("Could not finish GitHub App setup. Try again later.");
      });
    return () => {
      cancelled = true;
    };
  }, [code]);

  return (
    <>
      <PageHeader
        kicker="GITHUB APP"
        title="Finishing setup"
        subtitle="Completing the GitHub App connection for this Deploy Platform instance."
      />

      <div
        style={{
          background: M.surface,
          border: `1px solid ${M.line}`,
          borderRadius: M.rLg,
          padding: 32,
          fontFamily: M.fontSans,
          fontSize: 14,
          color: error ? M.err : M.fg,
        }}
      >
        {error && error}
        {!error && !result && "Finishing setup..."}
        {result && (
          <div>
            <div style={{ marginBottom: 12 }}>
              App {result.slug} created — install it on GitHub.
            </div>
            <a
              href={`https://github.com/apps/${result.slug}/installations/new`}
              style={{ color: M.accentLight }}
            >
              Install the App
            </a>
          </div>
        )}
      </div>
    </>
  );
}

export default function GitHubAppCallbackPage() {
  return (
    <AppShell>
      <Suspense>
        <GitHubAppCallbackContent />
      </Suspense>
    </AppShell>
  );
}
