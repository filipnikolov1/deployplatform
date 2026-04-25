"use client";

import { useEffect, useMemo, useState } from "react";
import { CodeBlock } from "./CodeBlock";
import { HealthChecklist } from "./HealthChecklist";
import { useDeployUrl } from "@/hooks/useDeployUrl";
import {
  generateDockerfile,
  generateWorkflow,
  techStackOptions,
  type TechStack,
} from "@/lib/setup/workflow-generators";

function SetupStep({ number, title, children }: { number: number; title: string; children: React.ReactNode }) {
  return (
    <section
      className="rounded-xl p-6 mb-3.5"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      <div className="flex items-center gap-3 mb-4">
        <span
          className="inline-flex h-[26px] w-[26px] items-center justify-center rounded-full text-[12px] font-semibold tabular-nums shrink-0"
          style={{
            background: "var(--c-accent-soft)",
            border: "1px solid var(--c-accent-line)",
            color: "var(--c-accent-fg)",
          }}
        >
          {number}
        </span>
        <h2 className="text-[15px] font-semibold tracking-[-0.005em] m-0" style={{ color: "var(--c-fg-0)" }}>
          {title}
        </h2>
      </div>
      {children}
    </section>
  );
}

export function SetupGuide() {
  const { url, isLoading } = useDeployUrl();
  const [appName, setAppName] = useState("my-app");
  const [branch, setBranch] = useState("main");
  const [stack, setStack] = useState<TechStack>("nodejs");
  const [port, setPort] = useState("3000");
  const [curlTemplate, setCurlTemplate] = useState<string>("");
  const [focusedField, setFocusedField] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch(`/api/deploy-hook/curl-template?app=${encodeURIComponent(appName)}`);
        if (!res.ok) return;
        const text = await res.text();
        if (!cancelled) setCurlTemplate(text);
      } catch { /* ignore */ }
    };
    if (appName) void load();
    return () => { cancelled = true; };
  }, [appName]);

  const workflow = useMemo(() => generateWorkflow(appName, branch, stack, port), [appName, branch, stack, port]);
  const dockerfile = useMemo(() => generateDockerfile(stack), [stack]);

  const field = (id: string, label: string, value: string, onChange: (v: string) => void, inputMode?: "numeric") => (
    <div>
      <label
        htmlFor={id}
        className="block text-[11px] font-semibold uppercase tracking-[0.12em] mb-1.5"
        style={{ color: "var(--c-fg-2)" }}
      >
        {label}
      </label>
      <input
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocusedField(id)}
        onBlur={() => setFocusedField(null)}
        inputMode={inputMode}
        className="w-full px-3 py-2.5 text-sm rounded-md outline-none transition-[border-color] duration-[120ms]"
        style={{
          background: "var(--c-surface-1)",
          border: `1px solid ${focusedField === id ? "var(--c-accent-line)" : "var(--c-border-2)"}`,
          color: "var(--c-fg-1)",
          fontFamily: "inherit",
        }}
      />
    </div>
  );

  return (
    <div>
      <SetupStep number={1} title="Repository">
        <div className="grid grid-cols-1 md:grid-cols-[2fr_1fr_1fr] gap-3 mb-4">
          {field("setup-app-name", "App name", appName, (v) => setAppName(v.toLowerCase().replace(/[^a-z0-9-]/g, "")))}
          {field("setup-branch", "Branch", branch, setBranch)}
          {field("setup-port", "Port", port, setPort, "numeric")}
        </div>
        <div>
          <p
            className="text-[11px] font-semibold uppercase tracking-[0.12em] mb-2"
            style={{ color: "var(--c-fg-2)" }}
          >
            Deploy hook endpoint
          </p>
          <CodeBlock code={isLoading ? "Loading deploy URL…" : url} language="url" />
        </div>
      </SetupStep>

      <SetupStep number={2} title="Tech stack">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          {techStackOptions.map((option) => {
            const sel = stack === option.value;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => setStack(option.value)}
                className="rounded-md px-3 py-3 text-[13px] font-medium transition-all duration-[120ms] outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
                style={{
                  background: sel ? "var(--c-accent-soft)" : "var(--c-surface-2)",
                  border: `1px solid ${sel ? "var(--c-accent-line)" : "var(--c-border-2)"}`,
                  color: sel ? "var(--c-accent-fg)" : "var(--c-fg-1)",
                  cursor: "pointer",
                }}
              >
                {option.label}
              </button>
            );
          })}
        </div>
      </SetupStep>

      <SetupStep number={3} title="GitHub Actions workflow">
        <CodeBlock code={workflow} language=".github/workflows/deploy.yml" />
      </SetupStep>

      <SetupStep number={4} title="Dockerfile">
        <CodeBlock code={dockerfile} language="Dockerfile" />
      </SetupStep>

      <SetupStep number={5} title="Manual deploy (curl)">
        <p className="text-[13px] mb-3" style={{ color: "var(--c-fg-2)" }}>
          Copy this command to trigger a deploy from your terminal for testing.
        </p>
        <CodeBlock code={curlTemplate || "# loading template…"} language="bash" />
      </SetupStep>

      <SetupStep number={6} title="Health checklist">
        <HealthChecklist />
      </SetupStep>
    </div>
  );
}
