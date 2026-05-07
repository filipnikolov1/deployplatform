package dev.filipnikolov.vector.analyzer.commit;

import dev.filipnikolov.vector.github.repo.RepoSlugResolver;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RepoSlugResolver} from {@code vector-github}.
 * Migrated from the old analyzer-local resolver as part of Track F.
 */
class RepoSlugResolverTest {

    private static String parseToString(String url) {
        return RepoSlugResolver.parse(url).map(dev.filipnikolov.vector.github.repo.RepoSlug::full).orElse(null);
    }

    @Test
    void parsesHttpsSshGitSuffixAndTrailingSlashForms() {
        assertEquals("owner/repo", parseToString("https://github.com/owner/repo"));
        assertEquals("owner/repo", parseToString("https://github.com/owner/repo.git"));
        assertEquals("owner/repo", parseToString("https://github.com/owner/repo/"));
        assertEquals("owner/repo", parseToString("git@github.com:owner/repo.git"));
    }

    @Test
    void rejectsMalformedOrNonGithubUrls() {
        assertTrue(RepoSlugResolver.parse("https://example.com/owner/repo").isEmpty());
        assertTrue(RepoSlugResolver.parse("not a url").isEmpty());
        assertTrue(RepoSlugResolver.parse("https://github.com/owner").isEmpty());
    }

    @Test
    void nullOrBlank_returnsEmpty() {
        assertTrue(RepoSlugResolver.parse(null).isEmpty());
        assertTrue(RepoSlugResolver.parse("").isEmpty());
    }
}
