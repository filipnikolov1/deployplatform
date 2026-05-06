"use client";

import { useEffect, useState } from "react";
import { GitCommit, RotateCcw, X } from "lucide-react";
import {
  useCommitDetail,
  useCommitLogs,
  useCommitDiff,
  useFileAtCommit,
} from "@/hooks/useCommitDetail";
import { VirtualizedLogViewer } from "./VirtualizedLogViewer";
import { DiffViewer } from "./DiffViewer";
import { FileViewer } from "./FileViewer";
import { RollbackModal } from "./RollbackModal";
import { useToast } from "@/hooks/useToast";
import { LoadingPanelState, PanelState } from "./PanelState";
import type { RecentDeploy } from "@/types/analyzer";

type TabId = "logs" | "diff" | "file";

const TABS: { id: TabId; label: string }[] = [
  { id: "logs", label: "Logs" },
  { id: "diff", label: "Diff" },
  { id: "file", label: "File" },
];

interface Props {
  appName: string;
  sha: string;
  currentSha: string | null;
  deploys: RecentDeploy[];
  pinnedImage: string | null;
  apiUnavailable: boolean;
  onClose: () => void;
  onRollbackSuccess: () => void;
}

export function CommitDetail({
  appName,
  sha,
  currentSha,
  deploys,
  pinnedImage,
  apiUnavailable,
  onClose,
  onRollbackSuccess,
}: Props) {
  const [tab, setTab] = useState<TabId>("logs");
  const [filePath, setFilePath] = useState("");
  const [showRollbackModal, setShowRollbackModal] = useState(false);
  const [rollingBack, setRollingBack] = useState(false);
  const [pinned, setPinned] = useState(!!pinnedImage);
  const toast = useToast();

  const { commit, isLoading: commitLoading, error: commitError } = useCommitDetail(appName, sha);
  const { logs, isLoading: logsLoading, error: logsError } = useCommitLogs(appName, sha);
  const { diff, isLoading: diffLoading, error: diffError } = useCommitDiff(appName, sha);
  const { file, isLoading: fileLoading, error: fileError } = useFileAtCommit(
    appName,
    tab === "file" ? sha : null,
    tab === "file" ? filePath || null : null,
  );

  const isCurrentSha = currentSha === sha;

  const targetEventId = deploys.find((d) => d.commitSha === sha)?.id ?? null;

  useEffect(() => {
    setPinned(!!pinnedImage);
  }, [pinnedImage]);

  const handleRollbackConfirm = async () => {
    if (!targetEventId || apiUnavailable) return;
    setRollingBack(true);
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(appName)}/rollback`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ eventId: targetEventId }),
      });
      if (res.status === 409) {
        toast.error("An operation is already in progress for this app. Try again shortly.");
        return;
      }
      if (res.status === 404) {
        toast.error("Deployment event no longer exists. The app may have been deleted.");
        return;
      }
      if (!res.ok) {
        toast.error("Rollback failed. Check the activity log for details.");
        return;
      }
      setPinned(true);
      setShowRollbackModal(false);
      onRollbackSuccess();
    } finally {
      setRollingBack(false);
    }
  };

  const handleUnpin = async () => {
    if (apiUnavailable) {
      toast.error("Deployments unavailable because vector-api is unreachable.");
      return;
    }
    const res = await fetch(`/api/apps/${encodeURIComponent(appName)}/unpin`, {
      method: "POST",
    });
    if (res.ok) {
      setPinned(false);
    } else {
      toast.error("Unpin failed. Try again.");
    }
  };

  return (
    <div
      className="rounded-xl overflow-hidden"
      style={{ background: "var(--c-surface-1)", border: "1px solid var(--c-border-1)" }}
    >
      {/* Header */}
      <div
        className="flex items-center gap-3 px-4 py-3"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <GitCommit className="h-4 w-4 shrink-0" style={{ color: "var(--c-fg-3)" }} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span
              className="font-mono text-[13px] font-medium"
              style={{ color: "var(--c-accent-fg)" }}
            >
              {sha.slice(0, 7)}
            </span>
          {commitLoading ? (
            <span className="text-[12px]" style={{ color: "var(--c-fg-3)" }}>
              Loading commit details...
            </span>
          ) : commit?.message ? (
              <span
                className="truncate text-[13px]"
                style={{ color: "var(--c-fg-2)" }}
                title={commit.message}
              >
                {commit.message.split("\n")[0]}
              </span>
            ) : null}
          </div>
          {commit?.author && (
            <div className="text-[11px] mt-0.5" style={{ color: "var(--c-fg-3)" }}>
              {commit.author}
              {commit.authoredAt
                ? ` · ${new Date(commit.authoredAt).toLocaleDateString()}`
                : ""}
            </div>
          )}
        </div>

        {/* Rollback button */}
        <button
          type="button"
          disabled={isCurrentSha || !targetEventId || rollingBack || apiUnavailable}
          onClick={() => setShowRollbackModal(true)}
          title={
            apiUnavailable
              ? "Deployments unavailable because vector-api is unreachable"
              : isCurrentSha
              ? "Already running this version"
              : !targetEventId
              ? "No deploy event exists for this commit"
              : "Roll back to this commit"
          }
          className="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-[12px] font-medium transition-[filter]"
          style={{
            background: isCurrentSha || apiUnavailable ? "var(--c-surface-2)" : "var(--c-accent-soft)",
            color: isCurrentSha || apiUnavailable ? "var(--c-fg-3)" : "var(--c-accent-fg)",
            border: `1px solid ${isCurrentSha || apiUnavailable ? "var(--c-border-2)" : "var(--c-accent-line)"}`,
            cursor: isCurrentSha || !targetEventId || apiUnavailable ? "not-allowed" : "pointer",
            opacity: isCurrentSha || !targetEventId || apiUnavailable ? 0.5 : 1,
          }}
        >
          <RotateCcw className="h-3 w-3" />
          {isCurrentSha ? "Running" : "Roll back to this"}
        </button>

        <button
          type="button"
          onClick={onClose}
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md"
          style={{ color: "var(--c-fg-3)" }}
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Pin banner */}
      {pinned && (
        <div
          className="flex items-center gap-3 px-4 py-2.5 text-[13px]"
          style={{
            background: "var(--c-accent-soft)",
            borderBottom: "1px solid var(--c-accent-line)",
          }}
        >
          <span style={{ color: "var(--c-accent-fg)" }}>
            App is pinned to commit{" "}
            <span className="font-mono">{sha.slice(0, 7)}</span>. New commits
            won&apos;t auto-deploy.
          </span>
          <button
            type="button"
            onClick={handleUnpin}
            className="ml-auto text-[12px] font-medium underline"
            style={{ color: "var(--c-accent-fg)" }}
          >
            {apiUnavailable ? "Unpin unavailable" : "Unpin"}
          </button>
        </div>
      )}

      {apiUnavailable && (
        <div
          className="px-4 py-2.5 text-[12px]"
          style={{
            background: "var(--c-status-building-bg)",
            borderBottom: "1px solid var(--c-status-building-line)",
            color: "var(--c-status-building-fg)",
          }}
        >
          vector-api is unreachable. Historical analysis is still visible, but rollback and unpin actions are disabled.
        </div>
      )}

      {commitError && (
        <PanelState
          tone="warning"
          title="Commit metadata unavailable"
          message="The analyzer could not load cached metadata for this commit. Logs, diff, and file contents may still be available."
        />
      )}

      {/* Tabs */}
      <div
        className="flex gap-0.5 px-4 pt-3 pb-0"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <div
          className="inline-flex gap-0.5 rounded-md p-[3px]"
          style={{ background: "var(--c-surface-2)", border: "1px solid var(--c-border-1)" }}
        >
          {TABS.map((t) => {
            const sel = t.id === tab;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                className="rounded-sm px-3.5 py-1.5 text-[13px] font-medium transition-all"
                style={{
                  background: sel ? "var(--c-accent-soft)" : "transparent",
                  color: sel ? "var(--c-accent-fg)" : "var(--c-fg-2)",
                  border: "none",
                  cursor: "pointer",
                }}
              >
                {t.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Tab content */}
      <div className="p-4">
        {tab === "logs" && (
          <>
            {logsLoading ? (
              <LoadingPanelState label="Loading historical logs..." />
            ) : logsError ? (
              <PanelState
                tone="warning"
                title="Logs unavailable"
                message="Historical log storage could not be reached for this commit."
              />
            ) : (
              <VirtualizedLogViewer logs={logs} height={400} />
            )}
          </>
        )}
        {tab === "diff" && (
          <DiffViewer diff={diff} isLoading={diffLoading} error={diffError} />
        )}
        {tab === "file" && (
          <FileViewer
            file={file}
            isLoading={fileLoading}
            error={fileError}
            currentPath={filePath}
            onPathChange={setFilePath}
          />
        )}
      </div>

      {showRollbackModal && (
        <RollbackModal
          sha={sha}
          onConfirm={handleRollbackConfirm}
          onCancel={() => setShowRollbackModal(false)}
          loading={rollingBack}
        />
      )}
    </div>
  );
}
