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
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* noop */
    }
  };

  return (
    <div className="relative">
      <div className="absolute top-2 right-2 z-10">
        <button
          type="button"
          onClick={handleCopy}
          aria-label={label}
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-white/[0.12] bg-white/[0.04] text-xs text-slate-200 hover:bg-white/[0.08] focus:outline-none focus-visible:ring-focus"
        >
          {copied ? (
            <>
              <Check className="h-3.5 w-3.5 text-green-400" />
              <span className="text-green-400">Copied</span>
            </>
          ) : (
            <>
              <Copy className="h-3.5 w-3.5" />
              <span>Copy</span>
            </>
          )}
        </button>
      </div>
      <div className="rounded-lg bg-black/40 border border-white/[0.08] p-4 overflow-x-auto">
        <pre className="text-xs text-slate-200 font-mono leading-relaxed">
          <code data-language={language}>{code}</code>
        </pre>
      </div>
    </div>
  );
}
