import type { SetupStatus } from "@/types/launchpad";

export const mockSetupStatus: SetupStatus = {
  secretConfigured: true,
  dockerReachable: true,
  githubTokenConfigured: false,
};
