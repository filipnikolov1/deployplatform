package dev.filipnikolov.vector.analyzer.crash;

import dev.filipnikolov.vector.ai.AiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisGeneratorServiceTest {

    @Test
    void marksPendingAndBroadcastsNarrationStartedAtGenerationStart() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiProvider aiProvider = mock(AiProvider.class);
        AnalysisStreamBroadcaster broadcaster = mock(AnalysisStreamBroadcaster.class);
        AnalysisGeneratorService service = new AnalysisGeneratorService(jdbc, aiProvider, broadcaster, 1);

        ResultSet rs = row();
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), anyLong()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });
        when(aiProvider.isAvailable()).thenReturn(false);

        service.regenerate(42);

        verify(jdbc).update(
                org.mockito.ArgumentMatchers.contains("ai_narration_status = ?"),
                eq(AnalysisGeneratorService.STATUS_PENDING),
                eq(42L));
        verify(broadcaster).publish(
                eq("docs"),
                eq("narrationStarted"),
                org.mockito.ArgumentMatchers.argThat(payload -> payload.toString().contains("PENDING")));
    }

    private static ResultSet row() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(42L);
        when(rs.getString("app_name")).thenReturn("docs");
        when(rs.getLong("crash_event_id")).thenReturn(7L);
        when(rs.getString("suspect_commit_sha")).thenReturn("bad");
        when(rs.getString("last_good_commit_sha")).thenReturn("good");
        when(rs.getString("suspect_file_path")).thenReturn("src/App.tsx");
        when(rs.getInt("suspect_line")).thenReturn(12);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getString("ai_narration")).thenReturn(null);
        when(rs.getString("ai_provider_used")).thenReturn(null);
        when(rs.getString("ai_narration_status")).thenReturn("UNAVAILABLE");
        when(rs.getInt("ai_regenerate_count")).thenReturn(0);
        when(rs.getString("ai_failure_reason")).thenReturn(null);
        when(rs.getString("evidence_json")).thenReturn("[]");
        when(rs.getString("signals_json")).thenReturn("{}");
        when(rs.getTimestamp("generated_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
        return rs;
    }
}
