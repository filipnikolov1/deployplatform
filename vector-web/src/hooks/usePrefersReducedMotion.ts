"use client";

import { useEffect, useState } from "react";
import { usePreferences } from "./usePreferences";

export function usePrefersReducedMotion(): boolean {
  const [systemReduced, setSystemReduced] = useState(false);
  const { prefs } = usePreferences();

  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) return;
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    setSystemReduced(mq.matches);
    const handler = (e: MediaQueryListEvent) => setSystemReduced(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);

  if (prefs.reduced_motion === "always") return true;
  if (prefs.reduced_motion === "never") return false;
  return systemReduced;
}
