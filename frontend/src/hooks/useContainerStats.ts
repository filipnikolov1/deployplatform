"use client";

import { useEffect, useState } from "react";
import type { ContainerStats } from "@/types/launchpad";
import { generateMockStats } from "@/lib/mocks/stats.mock";

export function useContainerStats(appName: string): {
  stats: ContainerStats | null;
  isLoading: boolean;
} {
  const [stats, setStats] = useState<ContainerStats | null>(null);

  useEffect(() => {
    setStats(generateMockStats());
    const interval = setInterval(() => setStats(generateMockStats()), 5_000);
    return () => clearInterval(interval);
  }, [appName]);

  return { stats, isLoading: stats === null };
}
