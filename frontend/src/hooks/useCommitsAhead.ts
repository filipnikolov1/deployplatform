"use client";

import { useEffect, useState } from "react";
import type { CommitsAhead } from "@/types/launchpad";
import { getMockCommitsAhead } from "@/lib/mocks/commitsAhead.mock";

export function useCommitsAhead(appName: string): {
  commitsAhead: CommitsAhead;
  isLoading: boolean;
} {
  const [commitsAhead, setCommitsAhead] = useState<CommitsAhead>({
    count: null,
    commits: [],
    compareUrl: "#",
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setIsLoading(true);
    const timer = setTimeout(() => {
      setCommitsAhead(getMockCommitsAhead(appName));
      setIsLoading(false);
    }, 130);
    return () => clearTimeout(timer);
  }, [appName]);

  return { commitsAhead, isLoading };
}
