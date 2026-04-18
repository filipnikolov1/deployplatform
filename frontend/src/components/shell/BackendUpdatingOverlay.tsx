"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2, RefreshCw, ServerCog } from "lucide-react";
import { Button } from "@/components/primitives/Button";
import {
  clearBackendUpdatePending,
  hasPendingBackendUpdate,
  markBackendUpdatePending,
  subscribeToBackendUpdateState,
} from "@/lib/self-update";
import { useSelfAppPending } from "@/hooks/useSelfAppPending";

const POLL_INTERVAL_MS = 1500;

type OverlayPhase = "hidden" | "waiting" | "ready";

export function BackendUpdatingOverlay() {
  const [pendingUpdate, setPendingUpdate] = useState(false);
  const [phase, setPhase] = useState<OverlayPhase>("hidden");
  const phaseRef = useRef<OverlayPhase>("hidden");
  const { pending: serverPending } = useSelfAppPending("launchpad-backend");

  useEffect(() => {
    phaseRef.current = phase;
  }, [phase]);

  useEffect(() => {
    if (serverPending && !hasPendingBackendUpdate()) {
      markBackendUpdatePending();
    }
  }, [serverPending]);

  useEffect(() => {
    const syncPendingState = () => {
      const pending = hasPendingBackendUpdate();
      setPendingUpdate(pending);
      if (!pending) {
        setPhase("hidden");
      }
    };

    syncPendingState();
    return subscribeToBackendUpdateState(syncPendingState);
  }, []);

  useEffect(() => {
    if (!pendingUpdate) {
      return;
    }

    let cancelled = false;

    const checkBackend = async () => {
      try {
        const response = await fetch(`/api/apps?_=${Date.now()}`, {
          cache: "no-store",
          credentials: "same-origin",
        });

        if (cancelled) {
          return;
        }

        if (response.ok) {
          if (phaseRef.current === "waiting") {
            setPhase("ready");
          }
          return;
        }

        if (response.status >= 500) {
          setPhase("waiting");
        }
      } catch {
        if (!cancelled) {
          setPhase("waiting");
        }
      }
    };

    void checkBackend();
    const interval = window.setInterval(() => {
      void checkBackend();
    }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [pendingUpdate]);

  if (phase === "hidden") {
    return null;
  }

  const ready = phase === "ready";

  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center px-4 py-8">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-md" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(167,139,250,0.14),transparent_28%),radial-gradient(circle_at_bottom,rgba(255,255,255,0.06),transparent_32%)]" />

      <div className="glass-panel relative w-full max-w-2xl overflow-hidden rounded-[28px] px-7 py-8 text-center sm:px-10 sm:py-10">
        <div className="absolute inset-x-10 top-0 h-px bg-gradient-to-r from-transparent via-white/20 to-transparent" />
        <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] shadow-[0_0_60px_rgba(167,139,250,0.18)]">
          {ready ? (
            <RefreshCw className="h-9 w-9 text-slate-100" />
          ) : (
            <ServerCog className="h-9 w-9 text-slate-100" />
          )}
        </div>

        <div className="mx-auto mb-4 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-4 py-2 text-[11px] uppercase tracking-[0.24em] text-slate-400">
          {ready ? "Update Complete" : "System Update"}
        </div>

        <h2 className="mx-auto max-w-xl text-3xl font-semibold tracking-[-0.03em] text-slate-50 sm:text-[2.6rem]">
          {ready ? "Backend is back online." : "Backend is updating. Please wait…"}
        </h2>

        <p className="mx-auto mt-4 max-w-xl text-sm leading-6 text-slate-400 sm:text-base">
          {ready
            ? "The API is responding again. Refresh this page to reconnect to the updated backend."
            : "Launchpad is restarting its backend container. The dashboard will keep checking in the background until the update finishes."}
        </p>

        <div className="mx-auto mt-8 flex max-w-md items-center justify-center gap-3 rounded-full border border-white/10 bg-black/35 px-5 py-3 text-sm text-slate-300 shadow-[inset_0_1px_0_rgba(255,255,255,0.05)]">
          {ready ? (
            <>
              <span className="h-2.5 w-2.5 rounded-full bg-emerald-400 shadow-[0_0_14px_rgba(74,222,128,0.85)]" />
              Refresh is available.
            </>
          ) : (
            <>
              <Loader2 className="h-4 w-4 animate-spin text-slate-300" />
              Waiting for backend to come back online…
            </>
          )}
        </div>

        <div className="mt-8">
          <Button
            type="button"
            disabled={!ready}
            onClick={() => {
              clearBackendUpdatePending();
              window.location.reload();
            }}
            className="min-w-48 justify-center rounded-full border-slate-500/20 bg-slate-600/20 px-6 py-3 text-slate-100 hover:border-slate-400/30 hover:bg-slate-500/28 disabled:border-slate-600/20 disabled:bg-slate-700/20 disabled:text-slate-500"
          >
            Refresh page
          </Button>
        </div>
      </div>
    </div>
  );
}
