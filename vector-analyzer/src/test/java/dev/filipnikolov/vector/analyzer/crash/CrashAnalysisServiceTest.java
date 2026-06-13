package dev.filipnikolov.vector.analyzer.crash;

import dev.filipnikolov.vector.analyzer.commit.GitHubCacheService;
import dev.filipnikolov.vector.analyzer.commit.RepoSlugResolver;
import dev.filipnikolov.vector.analyzer.common.AfterCommitRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                mock(AnalysisGeneratorService.class),
                mock(AfterCommitRunner.class));

        LocalDateTime crashTime = LocalDateTime.of(2026, 5, 6, 18, 30);
        when(jdbc.queryForList(anyString(), eq("docs"), eq("suspect"), eq(crashTime), eq(80)))
                .thenReturn(List.of(Map.of("line", "boom")));

        service.fetchCrashLogWindow("docs", crashTime, "suspect");

        verify(jdbc).queryForList(
                contains("commit_sha = ?"),
                eq("docs"),
                eq("suspect"),
                eq(crashTime),
                eq(80));
    }

    @Test
    void publishesAndGeneratesViaAfterCommitRunner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AnalysisStreamBroadcaster broadcaster = mock(AnalysisStreamBroadcaster.class);
        AnalysisGeneratorService generator = mock(AnalysisGeneratorService.class);
        AfterCommitRunner afterCommit = mock(AfterCommitRunner.class);
        CrashAnalysisService service = new CrashAnalysisService(
                jdbc, mock(GitHubCacheService.class), mock(RepoSlugResolver.class),
                broadcaster, generator, afterCommit);

        // CRASHED event row
        when(jdbc.queryForMap(anyString(), eq(7L)))
                .thenReturn(Map.of("id", 7L, "app_name", "myapp",
                        "created_at", LocalDateTime.of(2026, 6, 10, 12, 0)));
        // findByCrashEventId: first call (existing check) empty, second call (after insert) returns the row
        CrashAnalysis saved = new CrashAnalysis();
        saved.setId(42L);
        saved.setAppName("myapp");
        saved.setCrashEventId(7L);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(7L)))
                .thenReturn(List.of())
                .thenReturn(List.of(saved));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);

        service.generateForCrashEvent(7L);

        // Neither side effect may fire directly — both must go through the runner.
        verify(broadcaster, never()).publish(any(), any(), any());
        verify(generator, never()).generateInitialAsync(anyLong());

        ArgumentCaptor<Runnable> deferred = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).run(deferred.capture());
        deferred.getValue().run();

        verify(broadcaster).publish(eq("myapp"), eq("crashAnalysisCreated"), any());
        verify(generator).generateInitialAsync(42L);
    }
}
