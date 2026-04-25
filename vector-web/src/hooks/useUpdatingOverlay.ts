"use client";

import { useEffect, useRef, useState } from "react";
import { useSelfAppPending, type SelfUpdatePhase } from "./useSelfAppPending";

export type OverlayPhase = "idle" | "delayed" | "waiting" | "ready";

export interface OverlayView {
  phase: OverlayPhase;
  updatePhase: SelfUpdatePhase | null;
  targetSha: string | null;
}

interface CachedPending {
  phase: SelfUpdatePhase;
  targetSha: string;
  cachedAt: number;
}

interface Options {
  appName: string;
  probeUrl: string;
  cacheKey: string;
  delayMs?: number;
  probeIntervalMs?: number;
  cacheTtlMs?: number;
}

function readCache(key: string, ttlMs: number): CachedPending | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CachedPending;
    if (
      typeof parsed.cachedAt !== "number" ||
      Date.now() - parsed.cachedAt > ttlMs
    ) {
      window.localStorage.removeItem(key);
      return null;
    }
    return parsed;
  } catch {
    window.localStorage.removeItem(key);
    return null;
  }
}

function writeCache(key: string, entry: CachedPending) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(key, JSON.stringify(entry));
}

function clearCache(key: string) {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(key);
}

export function useUpdatingOverlay({
  appName,
  probeUrl,
  cacheKey,
  delayMs = 3000,
  probeIntervalMs = 1500,
  cacheTtlMs = 20 * 60 * 1000,
}: Options): OverlayView {
  const { pending, error, isLoading } = useSelfAppPending(appName);

  const [phase, setPhase] = useState<OverlayPhase>(() => {
    const cached = readCache(cacheKey, cacheTtlMs);
    if (!cached) return "idle";
    return Date.now() - cached.cachedAt >= delayMs ? "waiting" : "delayed";
  });

  const phaseRef = useRef(phase);
  useEffect(() => {
    phaseRef.current = phase;
  }, [phase]);

  useEffect(() => {
    if (pending) {
      writeCache(cacheKey, {
        phase: pending.phase,
        targetSha: pending.targetSha,
        cachedAt: Date.now(),
      });
      if (phaseRef.current === "idle") setPhase("delayed");
      return;
    }
    if (error || isLoading) return;
    if (phaseRef.current === "idle" || phaseRef.current === "delayed") {
      clearCache(cacheKey);
      if (phaseRef.current === "delayed") setPhase("idle");
    }
  }, [pending, error, isLoading, cacheKey]);

  useEffect(() => {
    if (phase !== "delayed") return;
    const cached = readCache(cacheKey, cacheTtlMs);
    const elapsed = cached ? Date.now() - cached.cachedAt : 0;
    const remaining = Math.max(0, delayMs - elapsed);
    const timer = window.setTimeout(() => setPhase("waiting"), remaining);
    return () => window.clearTimeout(timer);
  }, [phase, cacheKey, cacheTtlMs, delayMs]);

  useEffect(() => {
    if (phase !== "waiting") return;
    let cancelled = false;

    const probe = async () => {
      try {
        const sep = probeUrl.includes("?") ? "&" : "?";
        const res = await fetch(`${probeUrl}${sep}_=${Date.now()}`, {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (cancelled || !res.ok) return;
        if (!pending && !error) {
          setPhase("ready");
          clearCache(cacheKey);
        }
      } catch {
        // keep waiting
      }
    };

    void probe();
    const interval = window.setInterval(probe, probeIntervalMs);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [phase, probeUrl, probeIntervalMs, cacheKey, pending, error]);

  const cached = phase === "idle" ? null : readCache(cacheKey, cacheTtlMs);
  const updatePhase = pending?.phase ?? cached?.phase ?? null;
  const targetSha = pending?.targetSha ?? cached?.targetSha ?? null;

  return { phase, updatePhase, targetSha };
}
