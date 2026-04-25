import type { Deployment } from "@/types/deployment";

function splitImageName(imageName: string): { repo: string; tag: string | null } {
  const idx = imageName.lastIndexOf(":");
  if (idx === -1) {
    return { repo: imageName, tag: null };
  }
  return {
    repo: imageName.slice(0, idx),
    tag: imageName.slice(idx + 1),
  };
}

function extractGitSha(tag: string | null): string | null {
  if (!tag) {
    return null;
  }
  const match = /^git-([A-Fa-f0-9]{7,64})$/.exec(tag);
  return match ? match[1] : null;
}

export function getDisplayImageName(app: Deployment): string {
  const { repo, tag } = splitImageName(app.imageName);
  const tagSha = extractGitSha(tag);

  if (!app.isSelfApp || !tagSha || !app.commitSha) {
    return app.imageName;
  }

  const currentMatchesRunningCommit = app.commitSha.startsWith(tagSha);
  const hasNewerKnownVersion = !!app.latestKnownSha && app.latestKnownSha !== app.commitSha;

  if (currentMatchesRunningCommit && !hasNewerKnownVersion) {
    return `${repo}:latest`;
  }

  return app.imageName;
}

export function getDisplayImageParts(app: Deployment): { full: string; repo: string; tag: string | null } {
  const full = getDisplayImageName(app);
  const { repo, tag } = splitImageName(full);
  return { full, repo, tag };
}
