"use client";

import { useCallback } from "react";
import useSWR from "swr";
import type { UserPreferences } from "@/types/launchpad";

interface MeResponse {
  email: string;
  preferences: UserPreferences;
}

const DEFAULT_PREFS: UserPreferences = {
  notify_on_fail: true,
  notify_on_first_deploy: true,
  notify_on_crash: true,
  notify_on_rollback: false,
  layout_mode: "grid",
  reduced_motion: "system",
  pinned_apps: [],
};

const fetcher = async (url: string): Promise<MeResponse> => {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to load /api/me: ${res.status}`);
  const raw = await res.json();
  return {
    email: raw.email,
    preferences: { ...DEFAULT_PREFS, ...(raw.preferences ?? {}) },
  };
};

export function usePreferences() {
  const { data, mutate } = useSWR<MeResponse>("/api/me", fetcher, {
    revalidateOnFocus: false,
    dedupingInterval: 5_000,
  });

  const prefs: UserPreferences = data?.preferences ?? DEFAULT_PREFS;

  const update = useCallback(
    async (patch: Partial<UserPreferences>) => {
      const optimistic: MeResponse = {
        email: data?.email ?? "",
        preferences: { ...prefs, ...patch },
      };
      await mutate(
        async () => {
          const res = await fetch("/api/me/preferences", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(patch),
          });
          if (!res.ok) throw new Error(`PATCH failed: ${res.status}`);
          const raw = await res.json();
          return {
            email: raw.email,
            preferences: { ...DEFAULT_PREFS, ...(raw.preferences ?? {}) },
          };
        },
        {
          optimisticData: optimistic,
          rollbackOnError: true,
          revalidate: false,
        },
      );
    },
    [data, prefs, mutate],
  );

  return { prefs, update };
}
