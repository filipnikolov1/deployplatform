package dev.filipnikolov.vector.analyzer.timeline;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UptimeWalkTest {

    private final LocalDateTime since = LocalDateTime.of(2026, 6, 1, 0, 0);
    private final LocalDateTime now = LocalDateTime.of(2026, 6, 11, 0, 0); // 10-day window

    private static Map<String, Object> ev(String type, String status, LocalDateTime at) {
        return Map.of("event_type", type, "status", status, "created_at", at);
    }

    @Test
    void appRunningSinceBeforeWindowWithNoEventsIsFullUptime() {
        long up = TimelineController.computeUptimeMs(true, List.of(), since, now);
        assertThat(up).isEqualTo(java.time.Duration.between(since, now).toMillis());
    }

    @Test
    void restartedSuccessEndsDowntimeNotUptime() {
        // crash on day 2, recovery on day 3 → 1 day down, 9 days up
        List<Map<String, Object>> events = List.of(
                ev("CRASHED", "FAILURE", since.plusDays(2)),
                ev("RESTARTED", "SUCCESS", since.plusDays(3)));
        long up = TimelineController.computeUptimeMs(true, events, since, now);
        assertThat(up).isEqualTo(java.time.Duration.ofDays(9).toMillis());
    }

    @Test
    void consecutiveDeploysDoNotResetAccumulatedUptime() {
        // running all window, deploys on day 3 and day 6 → still 10 days up
        List<Map<String, Object>> events = List.of(
                ev("DEPLOY_FINISHED", "SUCCESS", since.plusDays(3)),
                ev("DEPLOY_FINISHED", "SUCCESS", since.plusDays(6)));
        long up = TimelineController.computeUptimeMs(true, events, since, now);
        assertThat(up).isEqualTo(java.time.Duration.ofDays(10).toMillis());
    }

    @Test
    void stoppedEndsUptime() {
        List<Map<String, Object>> events = List.of(
                ev("STOPPED", "SUCCESS", since.plusDays(4)));
        long up = TimelineController.computeUptimeMs(true, events, since, now);
        assertThat(up).isEqualTo(java.time.Duration.ofDays(4).toMillis());
    }

    @Test
    void startsDownWhenInitialStateIsDown() {
        List<Map<String, Object>> events = List.of(
                ev("DEPLOY_FINISHED", "SUCCESS", since.plusDays(8)));
        long up = TimelineController.computeUptimeMs(false, events, since, now);
        assertThat(up).isEqualTo(java.time.Duration.ofDays(2).toMillis());
    }
}
