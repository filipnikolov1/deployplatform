import type { CommitsAhead } from "@/types/launchpad";

const sample = [
  {
    sha: "ab12cd34",
    message: "Fix healthcheck retries during startup",
    author: "Filip",
    date: new Date().toISOString(),
    url: "https://github.com/example/repo/commit/ab12cd34",
  },
  {
    sha: "ef56gh78",
    message: "Tune nginx timeout defaults for websocket apps",
    author: "Filip",
    date: new Date().toISOString(),
    url: "https://github.com/example/repo/commit/ef56gh78",
  },
  {
    sha: "ij90kl12",
    message: "Refactor setup onboarding sections",
    author: "Filip",
    date: new Date().toISOString(),
    url: "https://github.com/example/repo/commit/ij90kl12",
  },
];

export function getMockCommitsAhead(appName: string): CommitsAhead {
  const hasAhead = appName === "api-gateway" || appName === "worker-service";
  return {
    count: hasAhead ? 3 : 0,
    commits: hasAhead ? sample : [],
    compareUrl: `https://github.com/example/${appName}/compare/main...HEAD`,
  };
}
