package dev.filipnikolov.vector.analyzer.crash;

import dev.filipnikolov.vector.analyzer.commit.GitHubCacheService;
import dev.filipnikolov.vector.analyzer.commit.RepoSlugResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrashAnalysisServiceTest {

    @Test
    void crashLogWindowFiltersBySuspectCommitSha() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrashAnalysisService service = new CrashAnalysisService(
                jdbc,
                mock(GitHubCacheService.class),
                mock(RepoSlugResolver.class),
                mock(AnalysisStreamBroadcaster.class),
                mock(AnalysisGeneratorService.class));

        LocalDateTime crashTime = LocalDateTime.of(2026, 5, 6, 18, 30);
        when(jdbc.queryForList(anyString(), eq("docs"), eq("suspect"), eq(crashTime), eq(80)))
                .thenReturn(List.of(Map.of("line", "boom")));

        service.fetchCrashLogWindow("docs", crashTime, "suspect");

        verify(jdbc).queryForList(
                org.mockito.ArgumentMatchers.contains("commit_sha = ?"),
                eq("docs"),
                eq("suspect"),
                eq(crashTime),
                eq(80));
    }
}
