"use client";

import { useEffect, useMemo, useState } from "react";
import { Rocket } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";
import { Input } from "@/components/primitives/Input";
import { Button } from "@/components/primitives/Button";
import { CodeBlock } from "./CodeBlock";
import { HealthChecklist } from "./HealthChecklist";
import { useDeployUrl } from "@/hooks/useDeployUrl";
import { useToast } from "@/hooks/useToast";
import {
  generateDockerfile,
  generateWorkflow,
  techStackOptions,
  type TechStack,
} from "@/lib/setup/workflow-generators";

export function SetupGuide() {
  const { url, isLoading } = useDeployUrl();
  const toast = useToast();
  const [appName, setAppName] = useState("my-app");
  const [branch, setBranch] = useState("main");
  const [stack, setStack] = useState<TechStack>("nodejs");
  const [port, setPort] = useState("3000");
  const [creating, setCreating] = useState(false);
  const [curlTemplate, setCurlTemplate] = useState<string>("");

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch(
          `/api/deploy-hook/curl-template?app=${encodeURIComponent(appName)}`,
        );
        if (!res.ok) return;
        const text = await res.text();
        if (!cancelled) setCurlTemplate(text);
      } catch {
        /* ignore */
      }
    };
    if (appName) void load();
    return () => {
      cancelled = true;
    };
  }, [appName]);

  const handleCreate = async () => {
    if (creating) return;
    setCreating(true);
    try {
      const res = await fetch("/api/setup/precreate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ appName, port: Number(port) || 3000 }),
      });
      if (!res.ok) {
        const msg = await res.text().catch(() => "");
        toast.error(msg || "Create failed");
        return;
      }
      toast.success(`${appName} created`);
    } catch {
      toast.error("Create failed");
    } finally {
      setCreating(false);
    }
  };

  const workflow = useMemo(
    () => generateWorkflow(appName, branch, stack, port),
    [appName, branch, stack, port],
  );
  const dockerfile = useMemo(() => generateDockerfile(stack), [stack]);

  return (
    <div className="space-y-6">
      <div className="glass-page-header">
        <div className="flex items-center gap-4">
          <div className="rounded-2xl border border-sky-200/20 bg-sky-300/10 p-3">
            <Rocket className="h-5 w-5 text-sky-200" />
          </div>
          <div>
            <div className="glass-kicker">First Deploy</div>
            <h1 className="glass-title text-3xl sm:text-3xl">Setup Guide</h1>
            <p className="glass-subtitle">
              Configure repository, workflow, and Docker image publishing.
            </p>
          </div>
        </div>
      </div>

      <GlassCard radius="panel" className="p-6">
        <h2 className="mb-2 text-lg font-semibold text-slate-100">
          1) Repository
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="md:col-span-2">
            <Input
              label="App name"
              value={appName}
              onChange={(e) =>
                setAppName(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ""))
              }
            />
          </div>
          <Input
            label="Branch"
            value={branch}
            onChange={(e) => setBranch(e.target.value)}
          />
          <Input
            label="Port"
            value={port}
            inputMode="numeric"
            onChange={(e) => setPort(e.target.value)}
          />
        </div>
        <div className="mt-4">
          <p className="mb-2 pl-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-300/72">
            Deploy hook endpoint
          </p>
          <CodeBlock code={isLoading ? "Loading deploy URL..." : url} language="text" />
        </div>
      </GlassCard>

      <GlassCard radius="panel" className="p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-2">2) Tech stack</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          {techStackOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setStack(option.value)}
              className={`rounded-2xl px-3 py-2.5 text-sm border backdrop-blur-xl transition-colors ${
                stack === option.value
                  ? "border-sky-200/30 bg-sky-300/12 text-sky-100"
                  : "border-white/[0.10] bg-white/[0.04] text-slate-300 hover:bg-white/[0.08]"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </GlassCard>

      <GlassCard radius="panel" className="p-6 space-y-4">
        <h2 className="text-lg font-semibold text-slate-100">3) Workflow</h2>
        <CodeBlock code={workflow} language="yaml" />
      </GlassCard>

      <GlassCard radius="panel" className="p-6 space-y-4">
        <h2 className="text-lg font-semibold text-slate-100">4) Dockerfile</h2>
        <CodeBlock code={dockerfile} language="dockerfile" />
      </GlassCard>

      <GlassCard radius="panel" className="p-6 space-y-3">
        <h2 className="text-lg font-semibold text-slate-100">
          5) Manual deploy (curl)
        </h2>
        <p className="text-sm text-slate-400">
          Copy this command to trigger a deploy from your terminal for testing.
        </p>
        <CodeBlock
          code={curlTemplate || "# loading template…"}
          language="bash"
        />
      </GlassCard>

      <GlassCard radius="panel" className="p-6 space-y-3">
        <h2 className="text-lg font-semibold text-slate-100">Health checklist</h2>
        <HealthChecklist />
        <Button type="button" onClick={handleCreate} disabled={creating || !appName}>
          {creating ? "Creating…" : "Create app"}
        </Button>
      </GlassCard>
    </div>
  );
}
