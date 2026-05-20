"use client";

import { useEffect, useRef, useState } from "react";
import type { ProgressFrame } from "@/types/vector";

const MAX_FRAMES = 50;

interface OperationProgressResult {
  stage: string | null;
  message: string | null;
  percent: number | null;
  frames: ProgressFrame[];
  /** True once the SSE stream has explicitly closed. False while connecting or connected. */
  streamClosed: boolean;
}

const EMPTY: OperationProgressResult = {
  stage: null,
  message: null,
  percent: null,
  frames: [],
  streamClosed: false,
};

export function useOperationProgress(operationId: string | null): OperationProgressResult {
  const [stage, setStage] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [percent, setPercent] = useState<number | null>(null);
  const [frames, setFrames] = useState<ProgressFrame[]>([]);
  const [streamClosed, setStreamClosed] = useState(false);
  const esRef = useRef<EventSource | null>(null);
  const activeRef = useRef(false);

  useEffect(() => {
    if (!operationId) {
      setStage(null);
      setMessage(null);
      setPercent(null);
      setFrames([]);
      setStreamClosed(false);
      return;
    }

    activeRef.current = true;
    setStage(null);
    setMessage(null);
    setPercent(null);
    setFrames([]);
    setStreamClosed(false);

    const es = new EventSource(`/api/operations/${encodeURIComponent(operationId)}/progress`);
    esRef.current = es;

    es.addEventListener("progress", (e: MessageEvent) => {
      if (!activeRef.current) return;
      try {
        const frame = JSON.parse(e.data as string) as ProgressFrame;
        setStage(frame.stage);
        setMessage(frame.message);

        if (frame.stage === "PULL_LAYER" && frame.current != null && frame.total != null && frame.total > 0) {
          setPercent(Math.round((frame.current / frame.total) * 100));
        } else {
          setPercent(null);
        }

        setFrames((prev) => {
          const next = [...prev, frame];
          return next.length > MAX_FRAMES ? next.slice(next.length - MAX_FRAMES) : next;
        });
      } catch {
        // ignore malformed frames
      }
    });

    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED) {
        if (activeRef.current) setStreamClosed(true);
        esRef.current = null;
      }
    };

    return () => {
      activeRef.current = false;
      es.close();
      esRef.current = null;
    };
  }, [operationId]);

  if (!operationId) return EMPTY;
  return { stage, message, percent, frames, streamClosed };
}
