"use client";

import { motion } from "framer-motion";
import ReactDiffViewer from "react-diff-viewer-continued";
import { M } from "@/design/tokens";
import { formatDistanceToNow } from "date-fns";

interface SideInfo {
  sha: string;
  label: "WORKING" | "BROKEN";
  content: string;
  age?: Date | null;
}

interface Props {
  filePath: string | null;
  working: SideInfo;
  broken: SideInfo;
  /** Line number to emphasize on broken side (crash line) */
  highlightLine?: number | null;
  added?: number;
  removed?: number;
}

const DIFF_STYLES = {
  variables: {
    dark: {
      diffViewerBackground: M.bg,
      diffViewerColor: M.fg,
      addedBackground: "rgba(134,239,172,0.08)",
      addedColor: M.ok,
      removedBackground: "rgba(252,165,165,0.08)",
      removedColor: M.err,
      wordAddedBackground: "rgba(134,239,172,0.18)",
      wordRemovedBackground: "rgba(252,165,165,0.18)",
      addedGutterBackground: "rgba(134,239,172,0.05)",
      removedGutterBackground: "rgba(252,165,165,0.05)",
      gutterBackground: M.surface,
      gutterBackgroundDark: M.surface2,
      highlightBackground: "rgba(167,139,250,0.10)",
      highlightGutterBackground: "rgba(167,139,250,0.10)",
      codeFoldBackground: M.surface2,
      emptyLineBackground: M.bg,
      gutterColor: M.fg4,
      codeFoldContentColor: M.fg3,
      diffViewerTitleBackground: M.surface,
      diffViewerTitleColor: M.fg2,
      diffViewerTitleBorderColor: M.line,
    },
  },
};

function SideHeader({ side }: { side: SideInfo }) {
  const isWorking = side.label === "WORKING";
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "10px 14px",
        borderBottom: `1px solid ${M.line}`,
        background: M.surface,
      }}
    >
      <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
        <span
          style={{
            padding: "2px 7px",
            borderRadius: M.rSm,
            fontSize: 10,
            fontWeight: 700,
            letterSpacing: "0.10em",
            textTransform: "uppercase",
            background: isWorking ? M.okSoft : M.errSoft,
            color: isWorking ? M.ok : M.err,
            border: `1px solid ${isWorking ? "rgba(134,239,172,0.22)" : "rgba(252,165,165,0.22)"}`,
          }}
        >
          {side.label}
        </span>
        <span
          style={{
            fontFamily: M.fontMono,
            fontSize: 12,
            color: M.fg,
            fontWeight: 500,
          }}
        >
          {side.sha.slice(0, 7)}
        </span>
      </span>
      {side.age && (
        <span style={{ fontSize: 11, color: M.fg3 }}>
          {formatDistanceToNow(side.age, { addSuffix: true })}
        </span>
      )}
    </div>
  );
}

export function DiffSideBySide({
  filePath,
  working,
  broken,
  highlightLine,
  added = 0,
  removed = 0,
}: Props) {
  const filename = filePath
    ? filePath.split("/").pop() ?? filePath
    : "unknown file";

  return (
    <div>
      {/* File header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 10,
          marginBottom: 12,
        }}
      >
        <span
          style={{
            fontFamily: M.fontMono,
            fontSize: 13,
            color: M.fg,
            fontWeight: 500,
          }}
        >
          {filename}
        </span>
        {(added > 0 || removed > 0) && (
          <span
            style={{
              padding: "1px 7px",
              borderRadius: M.rSm,
              fontSize: 10,
              fontWeight: 600,
              background: "transparent",
              color: M.fg3,
              border: `1px solid ${M.line2}`,
              fontFamily: M.fontMono,
            }}
          >
            +{added} −{removed}
          </span>
        )}
      </div>

      {/* Two-pane diff */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3 }}
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: 12,
        }}
      >
        {/* WORKING side */}
        <div
          style={{
            background: M.bg,
            border: `1px solid ${M.line}`,
            borderRadius: M.rLg,
            overflow: "hidden",
          }}
        >
          <SideHeader side={working} />
          <div style={{ maxHeight: 320, overflowY: "auto" }}>
            <ReactDiffViewer
              oldValue={working.content}
              newValue={working.content}
              splitView={false}
              useDarkTheme
              styles={DIFF_STYLES}
              hideLineNumbers={false}
              renderContent={(str) => <span style={{ fontFamily: M.fontMono, fontSize: 12 }}>{str}</span>}
            />
          </div>
        </div>

        {/* BROKEN side */}
        <div
          style={{
            background: M.bg,
            border: `1px solid ${M.line}`,
            borderRadius: M.rLg,
            overflow: "hidden",
          }}
        >
          <SideHeader side={broken} />
          <div style={{ maxHeight: 320, overflowY: "auto" }}>
            <ReactDiffViewer
              oldValue={working.content}
              newValue={broken.content}
              splitView={false}
              useDarkTheme
              styles={DIFF_STYLES}
              hideLineNumbers={false}
              highlightLines={highlightLine ? [`R-${highlightLine}`] : []}
              renderContent={(str) => <span style={{ fontFamily: M.fontMono, fontSize: 12 }}>{str}</span>}
            />
          </div>
        </div>
      </motion.div>
    </div>
  );
}
