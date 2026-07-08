package dev.filipnikolov.vector.githubapp;

/**
 * Normalizes a {@code Deployment.repoUrl} to the {@code owner/name} form used by
 * {@link dev.filipnikolov.vector.githubapp.model.GitHubRepo#getFullName()}, so the two can be
 * compared regardless of protocol prefix or trailing {@code .git}.
 */
public final class RepoUrlNormalizer {

    private RepoUrlNormalizer() {
    }

    /**
     * Returns the {@code owner/name} slug for a GitHub repo URL, or {@code null} if the URL is
     * blank or not a recognised GitHub URL.
     */
    public static String normalize(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }
        String trimmed = repoUrl.strip();
        String withoutPrefix = trimmed
                .replaceFirst("^https?://github\\.com/", "")
                .replaceFirst("^git@github\\.com:", "");
        while (withoutPrefix.endsWith("/")) {
            withoutPrefix = withoutPrefix.substring(0, withoutPrefix.length() - 1);
        }
        if (withoutPrefix.endsWith(".git")) {
            withoutPrefix = withoutPrefix.substring(0, withoutPrefix.length() - 4);
        }
        return withoutPrefix;
    }
}
