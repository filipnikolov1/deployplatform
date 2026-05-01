"use client";

import { useState } from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism";
import type { CommitFile } from "@/types/analyzer";

interface Props {
  file: CommitFile | null;
  isLoading: boolean;
  onPathChange: (path: string) => void;
  currentPath: string;
}

function detectLanguage(path: string): string {
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

export function FileViewer({ file, isLoading, onPathChange, currentPath }: Props) {
  const [inputPath, setInputPath] = useState(currentPath);

  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center h-32 text-[13px]"
        style={{ color: "var(--c-fg-3)" }}
      >
        Loading file…
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={inputPath}
          onChange={(e) => setInputPath(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") onPathChange(inputPath.trim());
          }}
          placeholder="src/path/to/file.ts"
          className="flex-1 rounded-md px-3 py-1.5 text-[13px] font-mono outline-none focus:ring-2 focus:ring-accent-light"
          style={{
            background: "var(--c-surface-2)",
            border: "1px solid var(--c-border-2)",
            color: "var(--c-fg-1)",
          }}
        />
        <button
          type="button"
          onClick={() => onPathChange(inputPath.trim())}
          className="rounded-md px-3 py-1.5 text-[13px] font-medium transition-[filter]"
          style={{
            background: "var(--c-accent-soft)",
            color: "var(--c-accent-fg)",
            border: "1px solid var(--c-accent-line)",
          }}
        >
          Load
        </button>
      </div>

      {!currentPath ? (
        <div
          className="flex items-center justify-center h-32 text-[13px]"
          style={{ color: "var(--c-fg-3)" }}
        >
          Enter a file path above to view its contents at this commit.
        </div>
      ) : !file?.available ? (
        <div
          className="flex items-center justify-center h-32 text-[13px]"
          style={{ color: "var(--c-fg-3)" }}
        >
          File unavailable — no GitHub token or file not found at this SHA.
        </div>
      ) : (
        <div
          style={{
            border: "1px solid var(--c-border-1)",
            borderRadius: "6px",
            overflow: "hidden",
          }}
        >
          <div
            className="flex items-center px-3 py-2 text-[12px]"
            style={{
              background: "var(--c-surface-1)",
              borderBottom: "1px solid var(--c-border-1)",
            }}
          >
            <span className="font-mono" style={{ color: "var(--c-fg-2)" }}>
              {file.path ?? currentPath}
            </span>
          </div>
          <SyntaxHighlighter
            language={detectLanguage(currentPath)}
            style={vscDarkPlus}
            showLineNumbers
            customStyle={{
              margin: 0,
              padding: "12px",
              background: "var(--c-surface-2)",
              fontSize: "12px",
              lineHeight: "18px",
              maxHeight: "480px",
              overflow: "auto",
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
