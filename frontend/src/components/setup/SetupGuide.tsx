"use client";

import { useMemo, useState } from "react";
import { Rocket } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";
import { CodeBlock } from "./CodeBlock";
import { HealthChecklist } from "./HealthChecklist";
import { useDeployUrl } from "@/hooks/useDeployUrl";
import {
  generateDockerfile,
  generateWorkflow,
  techStackOptions,
  type TechStack,
} from "@/lib/setup/workflow-generators";

export function SetupGuide() {
  const { url, isLoading } = useDeployUrl();
  const [appName, setAppName] = useState("my-app");
  const [branch, setBranch] = useState("main");
  const [stack, setStack] = useState<TechStack>("nodejs");
  const [port, setPort] = useState("3000");

  const workflow = useMemo(
    () => generateWorkflow(appName, branch, stack),
    [appName, branch, stack],
  );
  const dockerfile = useMemo(() => generateDockerfile(stack), [stack]);

  return (
    <div className="space-y-6">
      <div className="border-b border-white/[0.08] bg-white/[0.02] -mx-4 px-4 py-8 sm:-mx-8 sm:px-8 rounded-xl">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-purple-500/10 border border-purple-500/20">
            <Rocket className="h-5 w-5 text-purple-400" />
          </div>
          <div>
            <h1 className="text-3xl font-semibold tracking-tight text-slate-100">
              Setup Guide
            </h1>
            <p className="text-slate-400">
              Configure repository, workflow, and Docker image publishing.
            </p>
          </div>
        </div>
      </div>

      <GlassCard radius="panel" className="p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-2">
          1) Repository
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <label className="text-sm text-slate-300 md:col-span-2">
            App name
            <input
              value={appName}
              onChange={(e) =>
                setAppName(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ""))
              }
              className="mt-1.5 w-full rounded-full bg-surface-glass border border-border-glass px-4 py-2.5 text-base text-text-primary"
            />
          </label>
          <label className="text-sm text-slate-300">
            Branch
            <input
              value={branch}
              onChange={(e) => setBranch(e.target.value)}
              className="mt-1.5 w-full rounded-full bg-surface-glass border border-border-glass px-4 py-2.5 text-base text-text-primary"
            />
          </label>
          <label className="text-sm text-slate-300">
            Port
            <input
              value={port}
              onChange={(e) => setPort(e.target.value)}
              className="mt-1.5 w-full rounded-full bg-surface-glass border border-border-glass px-4 py-2.5 text-base text-text-primary"
            />
          </label>
        </div>
        <div className="mt-4">
          <p className="text-sm text-slate-400 mb-2">Deploy hook endpoint</p>
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
              className={`rounded-lg px-3 py-2 text-sm border transition-colors ${
                stack === option.value
                  ? "bg-purple-500/10 text-purple-400 border-purple-500/20"
                  : "bg-white/[0.04] text-slate-300 border-white/[0.08] hover:bg-white/[0.08]"
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
        <h2 className="text-lg font-semibold text-slate-100">Health checklist</h2>
        <HealthChecklist />
        <a
          href="/docs/setup#secret"
          className="inline-flex text-sm text-accent-ghostLight hover:text-white transition-colors"
        >
          Generating the deploy-hook secret
        </a>
        <button
          type="button"
          onClick={() => console.log("create app", { appName, port })}
          className="inline-flex items-center px-4 py-2 rounded-full bg-accent-ghost/30 border border-accent-ghostLight text-sm text-white hover:bg-accent-ghost/40 focus:outline-none focus-visible:ring-focus"
        >
          Create app
        </button>
      </GlassCard>
    </div>
  );
}
