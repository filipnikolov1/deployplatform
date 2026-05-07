"use client";

import { useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism";
import { M } from "@/design/tokens";
import { useFileAtCommit, useFileHistory } from "@/hooks/useCommitDetail";

interface Props {
  appName: string;
  suspectSha: string | null;
  filePath: string | null;
  highlightLine: number | null;
  onVersionChange?: (sha: string) => void;
}

function detectLanguage(path: string | null): string {
  if (!path) return "text";
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  const map: Record<string, string> = {
    ts: "typescript",
    tsx: "tsx",
    js: "javascript",
    jsx: "jsx",
    java: "java",
    py: "python",
    go: "go",
    rs: "rust",
    json: "json",
    yaml: "yaml",
    yml: "yaml",
    xml: "xml",
    html: "html",
    css: "css",
    sh: "bash",
    md: "markdown",
    sql: "sql",
  };
  return map[ext] ?? "text";
}

function shortPath(p: string | null): string {
  if (!p) return "—";
  const parts = p.split("/");
  return parts[parts.length - 1] ?? p;
}

export function CodePanel({
  appName,
  suspectSha,
  filePath,
  highlightLine,
  onVersionChange,
}: Props) {
  const { file, isLoading, error } = useFileAtCommit(appName, suspectSha, filePath);
  const { history } = useFileHistory(appName, filePath, 8);
  const containerRef = useRef<HTMLDivElement>(null);

  // Scroll to crash line
  useEffect(() => {
    if (!highlightLine || !containerRef.current) return;
    const el = containerRef.current.querySelector<HTMLElement>(
      `[data-line="${highlightLine}"]`
    );
    if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [highlightLine, file]);

  const headerName = shortPath(filePath);
  const headerSha = suspectSha ? suspectSha.slice(0, 7) : null;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 320 }}>
      {/* Header row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 10,
          gap: 8,
          flexWrap: "wrap",
        }}
      >
        <div
          style={{
            fontSize: 11,
            fontWeight: 600,
            color: M.fg3,
            letterSpacing: "0.16em",
            textTransform: "uppercase",
            fontFamily: M.fontMono,
          }}
        >
          {headerName}
          {headerSha && ` · ${headerSha}`}
        </div>

        {/* Version chip row */}
        {history.length > 0 && (
          <div
            style={{
              display: "inline-flex",
              gap: 4,
              padding: 3,
              background: M.surface,
              border: `1px solid ${M.line}`,
              borderRadius: M.rPill,
            }}
          >
            {history.slice(0, 6).map((entry) => {
              const isActive =
                suspectSha != null &&
                (entry.sha.startsWith(suspectSha) ||
                  suspectSha.startsWith(entry.sha.slice(0, 7)));
              return (
                <button
                  key={entry.sha}
                  type="button"
                  onClick={() => onVersionChange?.(entry.sha)}
                  style={{
                    position: "relative",
                    padding: "4px 10px",
                    borderRadius: M.rPill,
                    background: "transparent",
                    border: "none",
                    cursor: "pointer",
                    fontFamily: M.fontMono,
                    fontSize: 11,
                    fontWeight: 500,
                    color: isActive ? M.accentLight : M.fg2,
                  }}
                >
                  {isActive && (
                    <motion.span
                      layoutId="code-panel-version-pill"
                      style={{
                        position: "absolute",
                        inset: 0,
                        borderRadius: M.rPill,
                        background: M.accentSoft,
                        border: `1px solid ${M.accentLine}`,
                      }}
                      transition={{ type: "spring", stiffness: 380, damping: 32 }}
                    />
                  )}
                  <span style={{ position: "relative" }}>
                    {entry.sha.slice(0, 6)}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Code body */}
      <AnimatePresence mode="wait">
        <motion.div
          key={suspectSha ?? "empty"}
          initial={{ opacity: 0, x: 8 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -8 }}
          transition={{ duration: 0.22 }}
          style={{
            flex: 1,
            background: M.bg,
            border: `1px solid ${M.line}`,
            borderRadius: M.rLg,
            overflow: "hidden",
          }}
        >
          {!filePath ? (
            <div
              style={{
                padding: "32px 16px",
                textAlign: "center",
                color: M.fg3,
                fontSize: 13,
              }}
            >
              No suspect file identified.
            </div>
          ) : isLoading ? (
            <div
              style={{
                padding: "32px 16px",
                textAlign: "center",
                color: M.fg3,
                fontSize: 13,
              }}
            >
              Loading file at suspect commit...
            </div>
          ) : error ? (
            <div
              style={{
                padding: "32px 16px",
                textAlign: "center",
                color: M.fg3,
                fontSize: 13,
              }}
            >
              File unavailable. GitHub API access may be required.
            </div>
          ) : !file?.available ? (
            <div
              style={{
                padding: "32px 16px",
                textAlign: "center",
                color: M.fg3,
                fontSize: 13,
              }}
            >
              File not found at this SHA.
            </div>
          ) : (
            <div ref={containerRef} style={{ maxHeight: 340, overflowY: "auto" }}>
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
                        background: "rgba(252, 165, 165, 0.15)",
                        borderLeft: `3px solid ${M.err}`,
                      },
                    } as React.HTMLAttributes<HTMLElement>;
                  }
                  return {
                    "data-line": String(lineNumber),
                  } as React.HTMLAttributes<HTMLElement>;
                }}
                customStyle={{
                  margin: 0,
                  padding: "12px",
                  background: "transparent",
                  fontSize: "12px",
                  lineHeight: "18px",
                }}
                lineNumberStyle={{ color: M.fg4, minWidth: "36px" }}
              >
                {file.content ?? ""}
              </SyntaxHighlighter>
            </div>
          )}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
