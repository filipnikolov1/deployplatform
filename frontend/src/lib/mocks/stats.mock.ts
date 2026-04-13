import type { ContainerStats } from "@/types/launchpad";

export function generateMockStats(): ContainerStats {
  return {
    cpuPercent: 2 + Math.random() * 8,
    memoryUsedMB: 120 + Math.random() * 40,
    memoryLimitMB: 512,
    uptimeSeconds: Math.floor(
      (Date.now() - new Date("2026-04-13T00:00:00Z").getTime()) / 1000,
    ),
    restartCount: 0,
  };
}
