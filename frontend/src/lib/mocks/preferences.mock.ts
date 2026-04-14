import type { UserPreferences } from "@/types/launchpad";

export const mockPreferences: UserPreferences = {
  notify_on_fail: true,
  notify_on_first_deploy: true,
  notify_on_crash: true,
  notify_on_rollback: false,
  layout_mode: "grid",
  reduced_motion: "system",
  pinned_apps: ["api-gateway"],
};
