"use client";

import { useMemo, useState } from "react";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { GlassCard } from "@/components/primitives/GlassCard";
import { CodeBlock } from "@/components/setup/CodeBlock";
import { HealthChecklist } from "@/components/setup/HealthChecklist";
import { useDeployUrl } from "@/hooks/useDeployUrl";
import {
  generateDockerfile,
  generateWorkflow,
  techStackOptions,
  type TechStack,
} from "@/lib/setup/workflow-generators";

export default function SetupPage() {
  const { url, isLoading } = useDeployUrl();
  const [appName, setAppName] = useState("my-app");
  const [branch, setBranch] = useState("main");
  const [stack, setStack] = useState<TechStack>("nodejs");

  const workflow = useMemo(
    () => generateWorkflow(appName, branch, stack),
    [appName, branch, stack],
  );
  const dockerfile = useMemo(() => generateDockerfile(stack), [stack]);

  return (
    <ToastProvider>
      <AppShell>
        <div className="mx-auto max-w-5xl px-4 py-12 space-y-6">
          <header>
            <h1 className="text-3xl font-semibold tracking-tight text-slate-100 mb-2">
              Setup
            </h1>
            <p className="text-slate-400">
              Generate workflow snippets and verify Launchpad prerequisites.
            </p>
          </header>

          <GlassCard radius="panel" className="p-6">
            <h2 className="text-lg font-semibold text-slate-100 mb-2">
              1) Health checklist
            </h2>
            <p className="text-sm text-slate-400 mb-4">
              Make sure server prerequisites are configured before your first
              deployment.
            </p>
            <HealthChecklist />
          </GlassCard>

          <GlassCard radius="panel" className="p-6">
            <h2 className="text-lg font-semibold text-slate-100 mb-2">
              2) Configure your app
            </h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3 mb-4">
              <label className="text-sm text-slate-300">
                App name
                <input
                  value={appName}
                  onChange={(e) => setAppName(e.target.value)}
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
                Stack
                <select
                  value={stack}
                  onChange={(e) => setStack(e.target.value as TechStack)}
                  className="mt-1.5 w-full rounded-full bg-surface-glass border border-border-glass px-4 py-2.5 text-base text-text-primary"
                >
                  {techStackOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </GlassCard>

          <GlassCard radius="panel" className="p-6 space-y-4">
            <h2 className="text-lg font-semibold text-slate-100">
              3) GitHub Actions workflow
            </h2>
            <p className="text-sm text-slate-400">
              Save as <span className="font-mono">.github/workflows/deploy.yml</span>.
            </p>
            <CodeBlock code={workflow} language="yaml" />
          </GlassCard>

          <GlassCard radius="panel" className="p-6 space-y-4">
            <h2 className="text-lg font-semibold text-slate-100">
              4) Dockerfile starter
            </h2>
            <CodeBlock code={dockerfile} language="dockerfile" />
          </GlassCard>

          <GlassCard radius="panel" className="p-6 space-y-2">
            <h2 className="text-lg font-semibold text-slate-100">
              5) Repository secret
            </h2>
            <p className="text-sm text-slate-400">
              Set <span className="font-mono">LAUNCHPAD_URL</span> in your
              repository secrets to:
            </p>
            <CodeBlock
              code={isLoading ? "Loading deploy URL..." : url}
              language="text"
            />
          </GlassCard>
        </div>
      </AppShell>
    </ToastProvider>
  );
}
