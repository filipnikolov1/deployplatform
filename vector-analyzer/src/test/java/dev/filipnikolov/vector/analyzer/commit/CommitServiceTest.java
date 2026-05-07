package dev.filipnikolov.vector.analyzer.commit;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommitServiceTest {

    @Test
    void diffUsesPreviousCommitTimelineRowNotOnlyDeployRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        GitHubCacheService github = mock(GitHubCacheService.class);
        RepoSlugResolver resolver = mock(RepoSlugResolver.class);
        CommitService service = new CommitService(jdbc, github, resolver);

        when(resolver.resolveForApp("docs")).thenReturn("owner/repo");
        when(jdbc.queryForList(anyString(), eq("docs"), eq("new"), eq("docs"), eq("new")))
                .thenReturn(List.of(Map.of("commit_sha", "previous-commit")));
        when(github.fetchDiff("owner/repo", "previous-commit", "new")).thenReturn("{\"files\":[]}");

        Map<String, Object> result = service.getCommitDiff("docs", "new");

        assertTrue((Boolean) result.get("available"));
        assertEquals("previous-commit", result.get("baseSha"));
        verify(github).fetchDiff("owner/repo", "previous-commit", "new");
    }

    @Test
    void diffFallsBackToParentShaWhenNoPreviousTimelineRowExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        GitHubCacheService github = mock(GitHubCacheService.class);
        RepoSlugResolver resolver = mock(RepoSlugResolver.class);
        CommitService service = new CommitService(jdbc, github, resolver);

        when(resolver.resolveForApp("docs")).thenReturn("owner/repo");
        when(jdbc.queryForList(anyString(), eq("docs"), eq("new"), eq("docs"), eq("new")))
                .thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq("owner/repo"), eq("new")))
                .thenReturn(List.of(Map.of("parent_sha", "parent")));
        when(github.fetchDiff("owner/repo", "parent", "new")).thenReturn("{\"files\":[]}");

        Map<String, Object> result = service.getCommitDiff("docs", "new");

        assertTrue((Boolean) result.get("available"));
        assertEquals("parent", result.get("baseSha"));
    }
}
