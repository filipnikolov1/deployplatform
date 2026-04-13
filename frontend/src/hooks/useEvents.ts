"use client";

import { useEffect, useState } from "react";
import type { DeploymentEvent } from "@/types/launchpad";
import { mockEvents } from "@/lib/mocks/events.mock";

interface UseEventsArgs {
  appName?: string;
  limit?: number;
}

interface UseEventsResult {
  events: DeploymentEvent[];
  isLoading: boolean;
  error: Error | null;
}

export function useEvents({
  appName,
  limit = 50,
}: UseEventsArgs = {}): UseEventsResult {
  const [events, setEvents] = useState<DeploymentEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setIsLoading(true);
    const timer = setTimeout(() => {
      const filtered = appName
        ? mockEvents.filter((e) => e.appName === appName)
        : mockEvents;
      setEvents(filtered.slice(0, limit));
      setIsLoading(false);
    }, 150);
    return () => clearTimeout(timer);
  }, [appName, limit]);

  return { events, isLoading, error: null };
}
