"use client";

import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";
import { Skeleton } from "@/components/primitives/Skeleton";
import { Button } from "@/components/primitives/Button";

interface Props {
  appName: string;
  onClose: () => void;
}

type State =
  | { kind: "loading" }
  | { kind: "ok"; text: string }
  | { kind: "err"; msg: string };

export function AiAnalysisPanel({ appName, onClose }: Props) {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(
          `/api/ai/logs/analyze?app=${encodeURIComponent(appName)}`,
        );
        const body = await res.text();
        if (cancelled) return;
        if (!res.ok) {
          setState({ kind: "err", msg: body || `Error ${res.status}` });
          return;
        }
        setState({ kind: "ok", text: body });
      } catch (e) {
        if (!cancelled) setState({ kind: "err", msg: (e as Error).message });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [appName]);

  return (
    <GlassCard
      radius="card"
      className="p-4"
      aria-busy={state.kind === "loading"}
    >
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm font-semibold text-purple-300">AI Analysis</span>
        <Button variant="icon" aria-label="Close analysis" onClick={onClose}>
          <X size={16} />
        </Button>
      </div>
      {state.kind === "loading" && (
        <div className="flex flex-col gap-2" aria-label="Analyzing logs">
          <Skeleton variant="line" className="h-3 w-11/12" />
          <Skeleton variant="line" className="h-3 w-9/12" />
          <Skeleton variant="line" className="h-3 w-10/12" />
        </div>
      )}
      {state.kind === "ok" && (
        <pre className="whitespace-pre-wrap font-sans text-sm leading-[1.5] text-slate-200">
          {state.text}
        </pre>
      )}
      {state.kind === "err" && (
        <div role="alert" className="text-sm text-red-400">
          {state.msg}
        </div>
      )}
    </GlassCard>
  );
}
