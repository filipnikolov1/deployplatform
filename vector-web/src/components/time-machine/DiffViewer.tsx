"use client";

import { useMemo } from "react";
import ReactDiffViewer, { DiffMethod } from "react-diff-viewer-continued";
import type { CommitDiff } from "@/types/analyzer";

interface Props {
  diff: CommitDiff | null;
  isLoading: boolean;
}

interface GitHubFile {
  filename: string;
  patch?: string;
  status: string;
  additions: number;
  deletions: number;
}

function parseDiffFiles(diffJson: string): GitHubFile[] {
  try {
    const parsed = JSON.parse(diffJson);
    return (parsed.files as GitHubFile[]) ?? [];
  } catch {
    return [];
  }
}

function splitPatch(patch: string): { oldCode: string; newCode: string } {
  const oldLines: string[] = [];
  const newLines: string[] = [];
  for (const line of patch.split("\n")) {
    if (line.startsWith("@@")) {
      oldLines.push(line);
      newLines.push(line);
    } else if (line.startsWith("-")) {
      oldLines.push(line.slice(1));
    } else if (line.startsWith("+")) {
      newLines.push(line.slice(1));
    } else {
      oldLines.push(line.slice(1) ?? line);
      newLines.push(line.slice(1) ?? line);
    }
  }
  return { oldCode: oldLines.join("\n"), newCode: newLines.join("\n") };
}

export function DiffViewer({ diff, isLoading }: Props) {
  const files = useMemo(() => {
    if (!diff?.available || !diff.diffJson) return null;
    return parseDiffFiles(diff.diffJson);
  }, [diff]);

  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center h-32 text-[13px]"
        style={{ color: "var(--c-fg-3)" }}
      >
        Loading diff…
      </div>
    );
  }

  if (!diff?.available) {
    return (
      <div
        className="flex items-center justify-center h-32 text-[13px]"
        style={{ color: "var(--c-fg-3)" }}
      >
        Diff unavailable — no GitHub token configured or this is the first commit.
      </div>
    );
  }

  if (!files || files.length === 0) {
    return (
      <div
        className="flex items-center justify-center h-32 text-[13px]"
        style={{ color: "var(--c-fg-3)" }}
      >
        No file changes in this commit.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {files.map((file) => (
        <div
          key={file.filename}
          style={{
            border: "1px solid var(--c-border-1)",
            borderRadius: "6px",
            overflow: "hidden",
          }}
        >
          <div
            className="flex items-center gap-2 px-3 py-2 text-[12px]"
            style={{
              background: "var(--c-surface-1)",
              borderBottom: "1px solid var(--c-border-1)",
            }}
          >
            <span className="font-mono font-medium" style={{ color: "var(--c-fg-1)" }}>
              {file.filename}
            </span>
            <span className="ml-auto" style={{ color: "var(--c-status-running-fg)" }}>
              +{file.additions}
            </span>
            <span style={{ color: "var(--c-status-failed-fg)" }}>-{file.deletions}</span>
          </div>
          {file.patch ? (
            <div style={{ fontSize: "12px", lineHeight: "18px" }}>
              <ReactDiffViewer
                {...splitPatch(file.patch)}
                splitView={false}
                compareMethod={DiffMethod.LINES}
                useDarkTheme
                styles={{
                  variables: {
                    dark: {
                      diffViewerBackground: "transparent",
                      addedBackground: "rgba(0,255,128,0.08)",
                      removedBackground: "rgba(255,50,50,0.10)",
                      wordAddedBackground: "rgba(0,255,128,0.20)",
                      wordRemovedBackground: "rgba(255,50,50,0.20)",
                      addedGutterBackground: "rgba(0,255,128,0.12)",
                      removedGutterBackground: "rgba(255,50,50,0.12)",
                      gutterBackground: "var(--c-surface-2)",
                      gutterColor: "var(--c-fg-3)",
                      codeFoldBackground: "var(--c-surface-1)",
                    },
                  },
                  line: { fontSize: "12px", fontFamily: "monospace" },
                }}
              />
            </div>
          ) : (
            <div
              className="px-3 py-2 text-[12px]"
              style={{ color: "var(--c-fg-3)", fontFamily: "monospace" }}
            >
              Binary file or no patch available.
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
