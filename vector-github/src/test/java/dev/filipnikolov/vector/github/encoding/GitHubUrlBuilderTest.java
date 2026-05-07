package dev.filipnikolov.vector.github.encoding;

import dev.filipnikolov.vector.github.repo.RepoSlug;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubUrlBuilderTest {

    private static final RepoSlug SLUG = new RepoSlug("octocat", "Hello-World");

    // ---- compare ----

    @Test
    void compare_plainShas() {
        String url = GitHubUrlBuilder.compare(SLUG, "abc123", "def456");
        assertThat(url).isEqualTo(
                "https://api.github.com/repos/octocat/Hello-World/compare/abc123...def456");
    }

    @Test
    void compare_leadingZeroSha() {
        // SHA that starts with '0' must not be altered
        String url = GitHubUrlBuilder.compare(SLUG, "0abc123", "0def456");
        assertThat(url).contains("/compare/0abc123...0def456");
    }

    // ---- commits ----

    @Test
    void commits_branchWithSlash() {
        Instant since = Instant.parse("2026-01-01T00:00:00Z");
        String url = GitHubUrlBuilder.commits(SLUG, "feat/my-feature", since, 30);
        // '/' in branch name must be encoded as %2F
        assertThat(url).contains("sha=feat%2Fmy-feature");
        assertThat(url).doesNotContain("sha=feat/my-feature");
    }

    @Test
    void commits_plainBranch() {
        Instant since = Instant.parse("2026-05-01T00:00:00Z");
        String url = GitHubUrlBuilder.commits(SLUG, "main", since, 10);
        assertThat(url).startsWith("https://api.github.com/repos/octocat/Hello-World/commits");
        assertThat(url).contains("sha=main");
        assertThat(url).contains("per_page=10");
    }

    // ---- commit ----

    @Test
    void commit_plainSha() {
        String url = GitHubUrlBuilder.commit(SLUG, "cafebabe");
        assertThat(url).isEqualTo(
                "https://api.github.com/repos/octocat/Hello-World/commits/cafebabe");
    }

    // ---- contents ----

    @Test
    void contents_pathWithSpace() {
        String url = GitHubUrlBuilder.contents(SLUG, "abc123", "src/my file.ts");
        // spaces in path segments must be %20
        assertThat(url).contains("src/my%20file.ts");
        assertThat(url).doesNotContain("src/my file.ts");
        assertThat(url).doesNotContain("src/my+file.ts");
    }

    @Test
    void contents_pathWithHash() {
        String url = GitHubUrlBuilder.contents(SLUG, "abc123", "dir/file#tag.ts");
        // '#' must be percent-encoded in the path
        assertThat(url).contains("dir/file%23tag.ts");
        assertThat(url).doesNotContain("dir/file#tag.ts");
    }

    @Test
    void contents_slashSeparatorsPreserved() {
        // Path segment separators '/' must remain as '/', only within-segment chars are encoded
        String url = GitHubUrlBuilder.contents(SLUG, "abc123", "src/main/App.java");
        assertThat(url).contains("/contents/src/main/App.java");
    }

    @Test
    void contents_refIsEncoded() {
        String url = GitHubUrlBuilder.contents(SLUG, "abc123", "README.md");
        assertThat(url).contains("?ref=abc123");
    }

    // ---- commitsForPath ----

    @Test
    void commitsForPath_pathWithSpace() {
        String url = GitHubUrlBuilder.commitsForPath(SLUG, "dir/my file.ts", 5);
        assertThat(url).contains("path=dir%2Fmy%20file.ts");
        assertThat(url).contains("per_page=5");
    }

    // ---- encodeSegment / encodePath helpers ----

    @Test
    void encodeSegment_replacesPlusWithPercent20() {
        // URLEncoder would encode space as '+'; we want %20
        String encoded = GitHubUrlBuilder.encodeSegment("hello world");
        assertThat(encoded).isEqualTo("hello%20world");
        assertThat(encoded).doesNotContain("+");
    }

    @Test
    void encodePath_splitOnSlash() {
        String encoded = GitHubUrlBuilder.encodePath("a b/c#d");
        assertThat(encoded).isEqualTo("a%20b/c%23d");
    }
}
