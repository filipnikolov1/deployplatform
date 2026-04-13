"use client";

import { useCallback, useState } from "react";
import type { UserPreferences } from "@/types/launchpad";
import { mockAccount } from "@/lib/mocks/preferences.mock";

export function usePreferences() {
  const [prefs, setPrefs] = useState<UserPreferences>(mockAccount.preferences);

  const update = useCallback(async (patch: Partial<UserPreferences>) => {
    await new Promise((r) => setTimeout(r, 120));
    setPrefs((prev) => ({ ...prev, ...patch }));
  }, []);

  return { prefs, update, email: mockAccount.email };
}
