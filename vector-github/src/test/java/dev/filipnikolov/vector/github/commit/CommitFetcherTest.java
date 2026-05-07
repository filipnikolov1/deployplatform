package dev.filipnikolov.vector.github.commit;

import dev.filipnikolov.vector.github.client.GitHubClient;
import dev.filipnikolov.vector.github.repo.RepoSlug;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitFetcherTest {

    @Mock
    GitHubClient client;

    CommitFetcher fetcher;

    private static final RepoSlug SLUG = new RepoSlug("octocat", "Hello-World");

    @BeforeEach
    void setUp() {
        fetcher = new CommitFetcher(client);
    }

    // ---- listRecent ----

    @Test
    void listRecent_parsesNestedShape() {
        String json = """
                [
                  {
                    "sha": "abc123",
                    "parents": [{"sha": "parent1"}],
                    "commit": {
                      "author": {"name": "Alice", "date": "2026-01-01T10:00:00Z"},
                      "message": "feat: add thing"
                    }
                  }
                ]
                """;
        when(client.get(anyString(), eq(String.class))).thenReturn(json);

        List<CommitMetadata> commits = fetcher.listRecent(SLUG, "main", Instant.now().minusSeconds(3600), 30);

        assertThat(commits).hasSize(1);
        CommitMetadata c = commits.get(0);
        assertThat(c.sha()).isEqualTo("abc123");
        assertThat(c.parentSha()).isEqualTo("parent1");
        assertThat(c.author()).isEqualTo("Alice");
        assertThat(c.authoredAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(c.message()).isEqualTo("feat: add thing");
    }

    @Test
    void listRecent_multipleCommits_allParsed() {
        String json = """
                [
                  {
                    "sha": "sha1",
                    "parents": [{"sha": "sha0"}],
                    "commit": {
                      "author": {"name": "Bob", "date": "2026-02-01T00:00:00Z"},
                      "message": "second"
                    }
                  },
                  {
                    "sha": "sha0",
                    "parents": [],
                    "commit": {
                      "author": {"name": "Alice", "date": "2026-01-15T00:00:00Z"},
                      "message": "first"
                    }
                  }
                ]
                """;
        when(client.get(anyString(), eq(String.class))).thenReturn(json);

        List<CommitMetadata> commits = fetcher.listRecent(SLUG, "main", Instant.now().minusSeconds(3600), 30);

        assertThat(commits).hasSize(2);
        assertThat(commits.get(0).sha()).isEqualTo("sha1");
        assertThat(commits.get(1).sha()).isEqualTo("sha0");
    }

    @Test
    void listRecent_rootCommit_parentShaIsNull() {
        // A root commit has an empty parents array
        String json = """
                [
                  {
                    "sha": "root001",
                    "parents": [],
                    "commit": {
                      "author": {"name": "Alice", "date": "2026-01-01T00:00:00Z"},
                      "message": "initial commit"
                    }
                  }
                ]
                """;
        when(client.get(anyString(), eq(String.class))).thenReturn(json);

        List<CommitMetadata> commits = fetcher.listRecent(SLUG, "main", Instant.now().minusSeconds(3600), 30);

        assertThat(commits).hasSize(1);
        assertThat(commits.get(0).parentSha()).isNull();
    }

    @Test
    void listRecent_nullResponse_returnsEmpty() {
        when(client.get(anyString(), eq(String.class))).thenReturn(null);

        List<CommitMetadata> commits = fetcher.listRecent(SLUG, "main", Instant.now().minusSeconds(3600), 30);

        assertThat(commits).isEmpty();
    }

    // ---- getOne ----

    @Test
    void getOne_parsesCorrectly() {
        String json = """
                {
                  "sha": "deadbeef",
                  "parents": [{"sha": "cafebabe"}],
                  "commit": {
                    "author": {"name": "Charlie", "date": "2026-03-01T12:00:00Z"},
                    "message": "fix: resolve null pointer"
                  }
                }
                """;
        when(client.get(anyString(), eq(String.class))).thenReturn(json);

        Optional<CommitMetadata> result = fetcher.getOne(SLUG, "deadbeef");

        assertThat(result).isPresent();
        CommitMetadata c = result.get();
        assertThat(c.sha()).isEqualTo("deadbeef");
        assertThat(c.parentSha()).isEqualTo("cafebabe");
        assertThat(c.author()).isEqualTo("Charlie");
        assertThat(c.message()).isEqualTo("fix: resolve null pointer");
    }

    @Test
    void getOne_notFound_returnsEmpty() {
        // GitHubClient returns null on 404
        when(client.get(anyString(), eq(String.class))).thenReturn(null);

        Optional<CommitMetadata> result = fetcher.getOne(SLUG, "notfound");

        assertThat(result).isEmpty();
    }

    @Test
    void getOne_rootCommit_parentShaIsNull() {
        String json = """
                {
                  "sha": "0000root",
                  "parents": [],
                  "commit": {
                    "author": {"name": "Init", "date": "2026-01-01T00:00:00Z"},
                    "message": "init"
                  }
                }
                """;
        when(client.get(anyString(), eq(String.class))).thenReturn(json);

        Optional<CommitMetadata> result = fetcher.getOne(SLUG, "0000root");

        assertThat(result).isPresent();
        assertThat(result.get().parentSha()).isNull();
    }
}
