"use client";

import { useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/design/primitives";
import { M } from "@/design/tokens";

interface ManifestResponse {
  postUrl: string;
  manifest: Record<string, unknown>;
}

export default function GitHubAppSetupPage() {
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [manifestData, setManifestData] = useState<ManifestResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/github/app-manifest")
      .then((res) => {
        if (!res.ok) throw new Error("Failed to load manifest");
        return res.json();
      })
      .then((data: ManifestResponse) => {
        if (!cancelled) setManifestData(data);
      })
      .catch(() => {
        if (!cancelled) setError("Could not start GitHub App setup. Try again later.");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (manifestData && formRef.current) {
      formRef.current.submit();
    }
  }, [manifestData]);

  return (
    <AppShell>
      <PageHeader
        kicker="GITHUB APP"
        title="Connect GitHub"
        subtitle="Create a GitHub App for this Deploy Platform instance to connect repositories."
      />

      <div
        style={{
          background: M.surface,
          border: `1px solid ${M.line}`,
          borderRadius: M.rLg,
          padding: 32,
          color: M.fg3,
          fontFamily: M.fontSans,
          fontSize: 14,
        }}
      >
        {error ? error : "Redirecting to GitHub..."}
      </div>

      {manifestData && (
        <form
          ref={formRef}
          method="post"
          action={manifestData.postUrl}
          style={{ display: "none" }}
        >
          <input type="hidden" name="manifest" value={JSON.stringify(manifestData.manifest)} />
        </form>
      )}
    </AppShell>
  );
}
