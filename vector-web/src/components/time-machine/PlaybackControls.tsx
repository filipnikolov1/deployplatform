"use client";

import { Pause, Play, RotateCcw } from "lucide-react";

export type PlaybackSpeed = 1 | 2 | 4;

interface Props {
  isPlaying: boolean;
  speed: PlaybackSpeed;
  onPlay: () => void;
  onPause: () => void;
  onReplay: () => void;
  onSpeedChange: (s: PlaybackSpeed) => void;
}

export function PlaybackControls({
  isPlaying,
  speed,
  onPlay,
  onPause,
  onReplay,
  onSpeedChange,
}: Props) {
  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={isPlaying ? onPause : onPlay}
        aria-label={isPlaying ? "Pause" : "Play"}
        className="inline-flex items-center justify-center h-8 w-8 rounded-full transition-[filter] hover:brightness-110"
        style={{
          background: "var(--c-accent-soft)",
          color: "var(--c-accent-fg)",
          border: "1px solid var(--c-accent-line)",
        }}
      >
        {isPlaying ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
      </button>
      <button
        type="button"
        onClick={onReplay}
        aria-label="Replay"
        className="inline-flex items-center justify-center h-8 w-8 rounded-full transition-[filter] hover:brightness-110"
        style={{
          background: "var(--c-surface-2)",
          color: "var(--c-fg-2)",
          border: "1px solid var(--c-border-2)",
        }}
      >
        <RotateCcw className="h-3.5 w-3.5" />
      </button>
      <div className="flex items-center gap-1 ml-1">
        {[1, 2, 4].map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => onSpeedChange(s as PlaybackSpeed)}
            className="rounded-md px-2 py-1 text-[11px] font-medium tabular-nums transition-[filter]"
            style={{
              background: speed === s ? "var(--c-accent-soft)" : "var(--c-surface-2)",
              color: speed === s ? "var(--c-accent-fg)" : "var(--c-fg-3)",
              border: `1px solid ${speed === s ? "var(--c-accent-line)" : "var(--c-border-2)"}`,
            }}
          >
            {s}×
          </button>
        ))}
      </div>
    </div>
  );
}
