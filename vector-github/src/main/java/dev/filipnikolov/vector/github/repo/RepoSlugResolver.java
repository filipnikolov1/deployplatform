package dev.filipnikolov.vector.github.repo;

import java.net.URI;
import java.util.Optional;

/**
 * Parses a GitHub repository URL into a {@link RepoSlug}.
 *
 * <p>Handles all common forms:
 * <ul>
 *   <li>{@code https://github.com/owner/repo}</li>
 *   <li>{@code https://github.com/owner/repo.git}</li>
 *   <li>{@code https://github.com/owner/repo/}</li>
 *   <li>{@code https://github.com/owner/repo.git/}</li>
 *   <li>{@code git@github.com:owner/repo.git}</li>
 * </ul>
 */
public final class RepoSlugResolver {

    private RepoSlugResolver() {}

    /**
     * Parses the given URL into a {@link RepoSlug}.
     *
     * @param url repository URL in any of the accepted forms
     * @return the parsed slug, or {@link Optional#empty()} if the URL is not a recognised GitHub URL
     */
    public static Optional<RepoSlug> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        String trimmed = url.strip();
        String ownerAndRepo;

        if (trimmed.startsWith("git@github.com:")) {
            ownerAndRepo = trimmed.substring("git@github.com:".length());
        } else {
            try {
                URI uri = URI.create(trimmed);
                String scheme = uri.getScheme();
                if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                    return Optional.empty();
                }
                if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                    return Optional.empty();
                }
                ownerAndRepo = uri.getPath();
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        if (ownerAndRepo == null) {
            return Optional.empty();
        }

        // Strip leading/trailing slashes and the optional trailing .git
        ownerAndRepo = ownerAndRepo.strip();
        while (ownerAndRepo.startsWith("/")) ownerAndRepo = ownerAndRepo.substring(1);
        while (ownerAndRepo.endsWith("/")) ownerAndRepo = ownerAndRepo.substring(0, ownerAndRepo.length() - 1);
        if (ownerAndRepo.endsWith(".git")) {
            ownerAndRepo = ownerAndRepo.substring(0, ownerAndRepo.length() - 4);
        }
        // Strip trailing slash again in case the URL was "owner/repo.git/"
        while (ownerAndRepo.endsWith("/")) ownerAndRepo = ownerAndRepo.substring(0, ownerAndRepo.length() - 1);

        String[] parts = ownerAndRepo.split("/", -1);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new RepoSlug(parts[0], parts[1]));
    }
}
