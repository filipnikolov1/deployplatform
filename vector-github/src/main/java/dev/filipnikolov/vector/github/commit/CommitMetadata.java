package dev.filipnikolov.vector.github.commit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Flat representation of a single GitHub commit.
 *
 * <p>{@code parentSha} is {@code null} for root (initial) commits.
 */
public record CommitMetadata(
        String sha,
        String parentSha,
        String author,
        Instant authoredAt,
        String message
) {

    /**
     * Parses a single element from the GitHub {@code /commits} or {@code /commits/{sha}} response.
     *
     * <p>Expected shape:
     * <pre>
     * {
     *   "sha": "abc123",
     *   "parents": [ { "sha": "def456" } ],
     *   "commit": {
     *     "author": { "name": "Alice", "date": "2026-01-01T00:00:00Z" },
     *     "message": "fix: something"
     *   }
     * }
     * </pre>
     *
     * @param root JSON node for a single commit object
     * @return parsed metadata
     */
    public static CommitMetadata fromJsonNode(JsonNode root) {
        String sha = root.path("sha").asText(null);

        // parents array — first element's sha, or null for root commits
        String parentSha = null;
        JsonNode parents = root.path("parents");
        if (parents.isArray() && !parents.isEmpty()) {
            JsonNode firstParent = parents.get(0);
            String ps = firstParent.path("sha").asText(null);
            if (ps != null && !ps.isBlank()) {
                parentSha = ps;
            }
        }

        JsonNode commitNode = root.path("commit");
        JsonNode authorNode = commitNode.path("author");

        String author = authorNode.path("name").asText(null);
        String dateStr = authorNode.path("date").asText(null);
        Instant authoredAt = dateStr != null ? Instant.parse(dateStr) : null;
        String message = commitNode.path("message").asText(null);

        return new CommitMetadata(sha, parentSha, author, authoredAt, message);
    }
}
