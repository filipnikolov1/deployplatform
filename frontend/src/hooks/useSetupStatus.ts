"use client";

import { useEffect, useState } from "react";
import type { SetupStatus } from "@/types/launchpad";
import { mockSetupStatus } from "@/lib/mocks/setupStatus.mock";

export function useSetupStatus() {
  const [status, setStatus] = useState<SetupStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setStatus(mockSetupStatus);
      setIsLoading(false);
    }, 120);
    return () => clearTimeout(timer);
  }, []);

  return { status, isLoading };
}
