package dev.filipnikolov.vector.github.encoding;

import dev.filipnikolov.vector.github.repo.RepoSlug;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds properly URL-encoded GitHub REST API URLs.
 *
 * <p>All methods are static — this is a pure utility class.
 */
public final class GitHubUrlBuilder {

    private static final String BASE = "https://api.github.com";

    private GitHubUrlBuilder() {}

    /**
     * Compare endpoint: {@code /repos/{owner}/{repo}/compare/{base}...{head}}
     */
    public static String compare(RepoSlug slug, String baseSha, String headSha) {
        return BASE + "/repos/" + encodeSegment(slug.owner()) + "/" + encodeSegment(slug.name())
                + "/compare/" + encodeSegment(baseSha) + "..." + encodeSegment(headSha);
    }

    /**
     * List commits: {@code /repos/{owner}/{repo}/commits?sha={branch}&since={iso}&per_page={n}}
     */
    public static String commits(RepoSlug slug, String branch, Instant since, int perPage) {
        String url = BASE + "/repos/" + encodeSegment(slug.owner()) + "/" + encodeSegment(slug.name())
                + "/commits?sha=" + encodeParam(branch)
                + "&since=" + encodeParam(DateTimeFormatter.ISO_INSTANT.format(since))
                + "&per_page=" + perPage;
        return url;
    }

    /**
     * Single commit: {@code /repos/{owner}/{repo}/commits/{sha}}
     */
    public static String commit(RepoSlug slug, String sha) {
        return BASE + "/repos/" + encodeSegment(slug.owner()) + "/" + encodeSegment(slug.name())
                + "/commits/" + encodeSegment(sha);
    }

    /**
     * File contents at a ref: {@code /repos/{owner}/{repo}/contents/{path}?ref={sha}}
     *
     * <p>Each path segment is encoded independently so that legitimate {@code /} separators
     * are preserved while characters like {@code #}, spaces, and {@code +} are percent-encoded.
     */
    public static String contents(RepoSlug slug, String sha, String path) {
        String encodedPath = encodePath(path);
        return BASE + "/repos/" + encodeSegment(slug.owner()) + "/" + encodeSegment(slug.name())
                + "/contents/" + encodedPath + "?ref=" + encodeParam(sha);
    }

    /**
     * Commits touching a file: {@code /repos/{owner}/{repo}/commits?path={path}&per_page={n}}
     */
    public static String commitsForPath(RepoSlug slug, String path, int perPage) {
        return BASE + "/repos/" + encodeSegment(slug.owner()) + "/" + encodeSegment(slug.name())
                + "/commits?path=" + encodeParam(path) + "&per_page=" + perPage;
    }

    // ---- private helpers ----

    /**
     * Encodes a URL path segment (replaces {@code +} with {@code %20}).
     */
    static String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Encodes a query-parameter value (replaces {@code +} with {@code %20}).
     */
    static String encodeParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Encodes each path segment separately, rejoining with {@code /}.
     */
    static String encodePath(String path) {
        if (path == null || path.isEmpty()) return "";
        return Arrays.stream(path.split("/", -1))
                .map(GitHubUrlBuilder::encodeSegment)
                .collect(Collectors.joining("/"));
    }
}
