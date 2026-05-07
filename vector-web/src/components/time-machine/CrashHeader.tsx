"use client";

import { motion } from "framer-motion";
import { M } from "@/design/tokens";
import { PageHeader } from "@/design/primitives/PageHeader";
import { Button } from "@/design/primitives/Button";
import { formatDistanceToNow } from "date-fns";

interface Props {
  appName: string;
  apps: string[];
  crashedAt: Date | null;
  exitCode: number | null;
  onAppChange: (name: string) => void;
  onReplay: () => void;
  isPlaying: boolean;
  onTogglePlay: () => void;
}

export function CrashHeader({
  appName,
  apps,
  crashedAt,
  exitCode,
  onAppChange,
  onReplay,
  isPlaying,
  onTogglePlay,
}: Props) {
  const crashLabel = crashedAt
    ? `crashed ${formatDistanceToNow(crashedAt, { addSuffix: true })}${exitCode != null ? ` · exit ${exitCode}` : ""}`
    : "crash details";

  return (
    <PageHeader
      kicker="CRASH FORENSICS"
      title="Bug Time Machine"
      actions={
        <>
          <Button variant="ghost" leadingIcon="rewind" size="sm" onClick={onReplay}>
            Replay
          </Button>
          <Button
            variant={isPlaying ? "secondary" : "primary"}
            leadingIcon={isPlaying ? "pause" : "play"}
            size="sm"
            onClick={onTogglePlay}
          >
            {isPlaying ? "Pause" : "Play"}
          </Button>
        </>
      }
    />
  );
}

export function CrashAppSelector({
  appName,
  apps,
  crashLabel,
  onAppChange,
}: {
  appName: string;
  apps: string[];
  crashLabel: string;
  onAppChange: (name: string) => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 10,
        marginBottom: 32,
        flexWrap: "wrap",
      }}
    >
      {apps.length > 1 ? (
        <select
          value={appName}
          onChange={(e) => onAppChange(e.target.value)}
          style={{
            padding: "5px 10px",
            borderRadius: M.rPill,
            fontSize: 13,
            fontFamily: M.fontMono,
            background: M.surface2,
            border: `1px solid ${M.line2}`,
            color: M.fg,
            cursor: "pointer",
          }}
        >
          {apps.map((a) => (
            <option key={a} value={a}>
              {a}
            </option>
          ))}
        </select>
      ) : (
        <span
          style={{
            padding: "5px 10px",
            borderRadius: M.rPill,
            fontSize: 13,
            fontFamily: M.fontMono,
            background: M.surface2,
            border: `1px solid ${M.line2}`,
            color: M.fg,
          }}
        >
          {appName}
        </span>
      )}
      <span style={{ fontSize: 13, color: M.err, fontFamily: M.fontMono }}>
        · {crashLabel}
      </span>
    </motion.div>
  );
}
