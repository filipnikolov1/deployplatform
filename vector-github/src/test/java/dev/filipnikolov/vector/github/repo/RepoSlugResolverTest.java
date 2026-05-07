package dev.filipnikolov.vector.github.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepoSlugResolverTest {

    // ---- happy-path forms ----

    @Test
    void parse_httpsPlain() {
        Optional<RepoSlug> result = RepoSlugResolver.parse("https://github.com/octocat/Hello-World");
        assertThat(result).isPresent();
        assertThat(result.get().owner()).isEqualTo("octocat");
        assertThat(result.get().name()).isEqualTo("Hello-World");
    }

    @Test
    void parse_httpsDotGit() {
        Optional<RepoSlug> result = RepoSlugResolver.parse("https://github.com/octocat/Hello-World.git");
        assertThat(result).isPresent();
        assertThat(result.get().full()).isEqualTo("octocat/Hello-World");
    }

    @Test
    void parse_httpsTrailingSlash() {
        Optional<RepoSlug> result = RepoSlugResolver.parse("https://github.com/octocat/Hello-World/");
        assertThat(result).isPresent();
        assertThat(result.get().full()).isEqualTo("octocat/Hello-World");
    }

    @Test
    void parse_httpsDotGitTrailingSlash() {
        Optional<RepoSlug> result = RepoSlugResolver.parse("https://github.com/octocat/Hello-World.git/");
        assertThat(result).isPresent();
        assertThat(result.get().full()).isEqualTo("octocat/Hello-World");
    }

    @Test
    void parse_sshGitAt() {
        Optional<RepoSlug> result = RepoSlugResolver.parse("git@github.com:octocat/Hello-World.git");
        assertThat(result).isPresent();
        assertThat(result.get().owner()).isEqualTo("octocat");
        assertThat(result.get().name()).isEqualTo("Hello-World");
    }

    // ---- malformed / rejected inputs ----

    @Test
    void parse_null_returnsEmpty() {
        assertThat(RepoSlugResolver.parse(null)).isEmpty();
    }

    @Test
    void parse_blank_returnsEmpty() {
        assertThat(RepoSlugResolver.parse("   ")).isEmpty();
    }

    @Test
    void parse_empty_returnsEmpty() {
        assertThat(RepoSlugResolver.parse("")).isEmpty();
    }

    @Test
    void parse_nonGitHubHttpsUrl_returnsEmpty() {
        assertThat(RepoSlugResolver.parse("https://gitlab.com/octocat/Hello-World")).isEmpty();
    }

    @Test
    void parse_missingRepoName_returnsEmpty() {
        // Only owner, no repo
        assertThat(RepoSlugResolver.parse("https://github.com/octocat")).isEmpty();
    }

    @Test
    void parse_missingRepoNameTrailingSlash_returnsEmpty() {
        assertThat(RepoSlugResolver.parse("https://github.com/octocat/")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-url",
            "ftp://github.com/octocat/repo",
            "git@bitbucket.org:octocat/repo.git"
    })
    void parse_unrecognisedForms_returnEmpty(String url) {
        assertThat(RepoSlugResolver.parse(url)).isEmpty();
    }

    // ---- full() helper ----

    @Test
    void full_returnsOwnerSlashName() {
        RepoSlug slug = new RepoSlug("filipnikolov1", "vector");
        assertThat(slug.full()).isEqualTo("filipnikolov1/vector");
    }
}
