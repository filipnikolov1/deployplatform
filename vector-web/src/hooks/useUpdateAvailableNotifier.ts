"use client";

import { useEffect } from "react";
import { useEvents } from "@/hooks/useEvents";
import { toast } from "@/lib/toast";

const STORAGE_KEY = "vector:lastSeenUpdateAt";

export function useUpdateAvailableNotifier() {
  const { events } = useEvents({ limit: 10 });

  useEffect(() => {
    const lastSeen = Number(localStorage.getItem(STORAGE_KEY) ?? 0);
    const newest = events
      .filter((e) => e.eventType === "UPDATE_AVAILABLE")
      .find((e) => new Date(e.createdAt).getTime() > lastSeen);
    if (!newest) return;

    toast.info(`Update available for ${newest.appName}`, {
      duration: 8000,
    });
    localStorage.setItem(STORAGE_KEY, String(Date.now()));
  }, [events]);
}
