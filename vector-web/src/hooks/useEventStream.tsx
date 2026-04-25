"use client";

import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";

interface EventStreamCtx {
  lastEventPayload: string | null;
  sseConnected: boolean;
}

const defaultCtx: EventStreamCtx = { lastEventPayload: null, sseConnected: false };
const Ctx = createContext<EventStreamCtx>(defaultCtx);

export function EventStreamProvider({ children }: { children: ReactNode }) {
  const [lastEventPayload, setLastEventPayload] = useState<string | null>(null);
  const [sseConnected, setSseConnected] = useState(false);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    let active = true;

    function connect() {
      if (!active) return;
      const es = new EventSource("/api/events/stream");
      esRef.current = es;

      es.onopen = () => setSseConnected(true);

      es.addEventListener("deployment-event", (e: MessageEvent) => {
        setLastEventPayload(e.data as string);
      });

      es.onerror = () => {
        setSseConnected(false);
        if (es.readyState === EventSource.CLOSED) {
          // Server returned non-2xx or permanently closed — schedule manual reconnect
          setTimeout(() => { if (active) connect(); }, 3_000);
        }
        // If CONNECTING, browser is already retrying; do nothing
      };
    }

    connect();

    return () => {
      active = false;
      esRef.current?.close();
    };
  }, []);

  return (
    <Ctx.Provider value={{ lastEventPayload, sseConnected }}>
      {children}
    </Ctx.Provider>
  );
}

export function useEventStream(): EventStreamCtx {
  return useContext(Ctx);
}
