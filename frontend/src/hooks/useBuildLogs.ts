"use client";

import { useEffect, useRef, useState } from "react";

const RING_BUFFER_CAP = 5000;

export type BuildLogStatus = "connecting" | "open" | "closed" | "error";

export function useBuildLogs(appName: string) {
  const [lines, setLines] = useState<string[]>([]);
  const [status, setStatus] = useState<BuildLogStatus>("connecting");
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    setLines([]);
    setStatus("connecting");
    const es = new EventSource(
      `/api/apps/${encodeURIComponent(appName)}/logs/build`,
    );
    esRef.current = es;

    es.onopen = () => setStatus("open");
    es.onmessage = (e: MessageEvent) => {
      setLines((prev) => {
        const next = prev.concat(e.data as string);
        if (next.length > RING_BUFFER_CAP) {
          return next.slice(next.length - RING_BUFFER_CAP);
        }
        return next;
      });
    };
    es.onerror = () => setStatus("error");

    return () => {
      es.close();
      setStatus("closed");
    };
  }, [appName]);

  return { lines, status };
}
