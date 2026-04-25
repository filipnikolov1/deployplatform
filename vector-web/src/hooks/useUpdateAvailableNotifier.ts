"use client";

import { useEffect } from "react";
import { useEvents } from "@/hooks/useEvents";
import { useToast } from "@/hooks/useToast";

const STORAGE_KEY = "vector:lastSeenUpdateAt";

export function useUpdateAvailableNotifier() {
  const { events } = useEvents({ limit: 10 });
  const toast = useToast();

  useEffect(() => {
    const lastSeen = Number(localStorage.getItem(STORAGE_KEY) ?? 0);
    const newest = events
      .filter((e) => e.eventType === "UPDATE_AVAILABLE")
      .find((e) => new Date(e.createdAt).getTime() > lastSeen);
    if (!newest) return;

    toast.info(`Update available for ${newest.appName}`, {
      durationMs: 8000,
    });
    localStorage.setItem(STORAGE_KEY, String(Date.now()));
  }, [events, toast]);
}
