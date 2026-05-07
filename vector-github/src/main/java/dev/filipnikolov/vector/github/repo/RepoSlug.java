package dev.filipnikolov.vector.github.repo;

/**
 * Immutable pair of GitHub owner and repository name.
 *
 * <p>Both fields are validated non-blank at construction time.
 */
public record RepoSlug(String owner, String name) {

    public RepoSlug {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("RepoSlug owner must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("RepoSlug name must not be blank");
        }
    }

    /** Returns {@code "owner/name"} in the form used by GitHub API paths. */
    public String full() {
        return owner + "/" + name;
    }
}
