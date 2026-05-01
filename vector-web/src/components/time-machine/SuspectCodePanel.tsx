"use client";

import { useEffect, useRef } from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism";
import { useFileAtCommit } from "@/hooks/useCommitDetail";

interface Props {
  appName: string;
  suspectSha: string | null;
  filePath: string | null;
  highlightLine: number | null;
}

function detectLanguage(path: string | null): string {
  if (!path) return "text";
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  const map: Record<string, string> = {
    ts: "typescript", tsx: "tsx", js: "javascript", jsx: "jsx",
    java: "java", py: "python", go: "go", rs: "rust",
    json: "json", yaml: "yaml", yml: "yaml", xml: "xml",
    html: "html", css: "css", scss: "scss",
    sh: "bash", md: "markdown", sql: "sql",
  };
  return map[ext] ?? "text";
}

export function SuspectCodePanel({ appName, suspectSha, filePath, highlightLine }: Props) {
  const { file, isLoading } = useFileAtCommit(appName, suspectSha, filePath);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!highlightLine || !containerRef.current) return;
    // Scroll to roughly the right area
    const el = containerRef.current.querySelector<HTMLElement>(`[data-line="${highlightLine}"]`);
    if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [highlightLine, file]);

  return (
    <div
      className="rounded-xl overflow-hidden"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      <div
        className="flex items-center justify-between px-4 py-3 gap-2"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <span className="text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
          Suspect file
        </span>
        <span className="font-mono text-[11px]" style={{ color: "var(--c-fg-3)" }}>
          {filePath ?? "—"}
          {suspectSha && ` @ ${suspectSha.slice(0, 7)}`}
          {highlightLine ? ` :${highlightLine}` : ""}
        </span>
      </div>

      {!filePath ? (
        <div className="px-4 py-8 text-center text-[13px]" style={{ color: "var(--c-fg-3)" }}>
          No suspect file identified — stack trace was empty and the suspect commit
          changed multiple files.
        </div>
      ) : isLoading ? (
        <div className="px-4 py-8 text-center text-[13px]" style={{ color: "var(--c-fg-3)" }}>
          Loading file at suspect commit…
        </div>
      ) : !file?.available ? (
        <div className="px-4 py-8 text-center text-[13px]" style={{ color: "var(--c-fg-3)" }}>
          File unavailable — repo not configured or file not found at this SHA.
        </div>
      ) : (
        <div ref={containerRef} style={{ maxHeight: "420px", overflow: "auto" }}>
          <SyntaxHighlighter
            language={detectLanguage(filePath)}
            style={vscDarkPlus}
            showLineNumbers
            wrapLines
            lineProps={(lineNumber: number) => {
              if (highlightLine && lineNumber === highlightLine) {
                return {
                  "data-line": String(lineNumber),
                  style: {
                    display: "block",
                    background: "rgba(248, 113, 113, 0.15)",
                    borderLeft: "3px solid var(--c-status-failed-fg)",
                  },
                } as React.HTMLAttributes<HTMLElement>;
              }
              return { "data-line": String(lineNumber) } as React.HTMLAttributes<HTMLElement>;
            }}
            customStyle={{
              margin: 0,
              padding: "12px",
              background: "var(--c-surface-2)",
              fontSize: "12px",
              lineHeight: "18px",
            }}
            lineNumberStyle={{ color: "var(--c-fg-3)", minWidth: "36px" }}
          >
            {file.content ?? ""}
          </SyntaxHighlighter>
        </div>
      )}
    </div>
  );
}
