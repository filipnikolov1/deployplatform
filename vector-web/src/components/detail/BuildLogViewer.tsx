"use client";

import { useEffect, useRef, useState } from "react";
import { Sparkles } from "lucide-react";
import { useBuildLogs } from "@/hooks/useBuildLogs";
import { Button } from "@/components/primitives/Button";
import { GlassCard } from "@/components/primitives/GlassCard";
import { AiAnalysisPanel } from "./AiAnalysisPanel";

interface Props {
  appName: string;
}

export function BuildLogViewer({ appName }: Props) {
  const { lines, status } = useBuildLogs(appName);
  const paneRef = useRef<HTMLPreElement>(null);
  const [stickyBottom, setStickyBottom] = useState(true);
  const [analysisOpen, setAnalysisOpen] = useState(false);

  useEffect(() => {
    const pane = paneRef.current;
    if (!pane) return;
    const onScroll = () => {
      const atBottom =
        pane.scrollHeight - pane.scrollTop - pane.clientHeight < 16;
      setStickyBottom(atBottom);
    };
    pane.addEventListener("scroll", onScroll);
    return () => pane.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    const pane = paneRef.current;
    if (pane && stickyBottom) {
      pane.scrollTop = pane.scrollHeight;
    }
  }, [lines, stickyBottom]);

  const empty = lines.length === 0;

  return (
    <div className="relative flex h-full min-h-0 flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="font-mono text-xs text-slate-400">
          {status === "connecting" && "connecting…"}
          {status === "open" && `${lines.length} lines`}
          {status === "error" && "connection error"}
          {status === "closed" && "closed"}
        </span>
        <Button
          variant="ghost-purple"
          leadingIcon={<Sparkles size={16} />}
          onClick={() => setAnalysisOpen(true)}
        >
          Ask AI: Analyze Logs
        </Button>
      </div>

      <GlassCard radius="card" className="flex min-h-0 flex-1 overflow-hidden p-0">
        <pre
          ref={paneRef}
          className="flex-1 min-h-0 overflow-auto whitespace-pre px-4 py-3 font-mono text-xs leading-[1.4] text-green-300 tabular-nums"
        >
          {empty ? (
            <span className="text-slate-400">Waiting for logs...</span>
          ) : (
            lines.join("\n")
          )}
        </pre>
      </GlassCard>

      {analysisOpen && (
        <div className="absolute inset-0 z-20 flex items-start justify-center">
          <button
            type="button"
            aria-label="Close analysis"
            onClick={() => setAnalysisOpen(false)}
            className="absolute inset-0 cursor-default bg-black/50 backdrop-blur-sm"
          />
          <div className="relative z-10 mx-4 mt-10 flex max-h-[calc(100%-3rem)] w-full max-w-2xl flex-col overflow-hidden">
            <AiAnalysisPanel appName={appName} onClose={() => setAnalysisOpen(false)} />
          </div>
        </div>
      )}
    </div>
  );
}
