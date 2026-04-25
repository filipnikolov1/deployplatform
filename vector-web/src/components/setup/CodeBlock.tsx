"use client";

import { useState } from "react";
import { Check, Copy } from "lucide-react";

interface Props {
  code: string;
  language?: string;
  label?: string;
}

export function CodeBlock({ code, language, label = "Copy" }: Props) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch { /* noop */ }
  };

  return (
    <div className="relative">
      {/* Copy button */}
      <button
        type="button"
        onClick={handleCopy}
        aria-label={label}
        className="absolute top-2.5 right-2.5 z-10 inline-flex items-center gap-1.5 rounded px-2.5 py-[5px] text-[11.5px] font-medium transition-all duration-fast outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
        style={{
          background: copied ? "rgba(34,197,94,0.12)" : "var(--c-surface-2)",
          border: `1px solid ${copied ? "rgba(34,197,94,0.28)" : "var(--c-border-2)"}`,
          color: copied ? "#86EFAC" : "var(--c-fg-1)",
        }}
      >
        {copied ? (
          <>
            <Check className="h-3 w-3" />
            Copied
          </>
        ) : (
          <>
            <Copy className="h-3 w-3" />
            Copy
          </>
        )}
      </button>

      {/* Code area */}
      <div
        className="rounded-[10px] p-4 overflow-x-auto"
        style={{
          background: "#040408",
          border: "1px solid var(--c-border-1)",
        }}
      >
        {language && (
          <span
            className="block mb-4 text-[10px] font-semibold uppercase tracking-[0.12em]"
            style={{ color: "var(--c-fg-3)", fontFamily: "inherit" }}
          >
            {language}
          </span>
        )}
        <pre className="text-xs text-slate-300 font-mono leading-relaxed m-0" style={{ whiteSpace: "pre" }}>
          <code>{code}</code>
        </pre>
      </div>
    </div>
  );
}
