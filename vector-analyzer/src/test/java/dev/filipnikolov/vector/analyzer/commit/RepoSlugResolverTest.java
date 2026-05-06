package dev.filipnikolov.vector.analyzer.commit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RepoSlugResolverTest {

    @Test
    void parsesHttpsSshGitSuffixAndTrailingSlashForms() {
        assertEquals("owner/repo", RepoSlugResolver.parse("https://github.com/owner/repo"));
        assertEquals("owner/repo", RepoSlugResolver.parse("https://github.com/owner/repo.git"));
        assertEquals("owner/repo", RepoSlugResolver.parse("https://github.com/owner/repo/"));
        assertEquals("owner/repo", RepoSlugResolver.parse("git@github.com:owner/repo.git"));
    }

    @Test
    void rejectsMalformedOrNonGithubUrls() {
        assertNull(RepoSlugResolver.parse("https://example.com/owner/repo"));
        assertNull(RepoSlugResolver.parse("not a url"));
        assertNull(RepoSlugResolver.parse("https://github.com/owner"));
    }
}
