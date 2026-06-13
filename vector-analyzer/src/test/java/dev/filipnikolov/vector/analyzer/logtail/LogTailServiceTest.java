package dev.filipnikolov.vector.analyzer.logtail;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogTailServiceTest {

    @Test
    void offsetTimestampsAreNormalizedToUtc() {
        LocalDateTime arrival = LocalDateTime.of(2026, 6, 10, 12, 0);
        var parsed = LogTailService.parseLogLine("2026-06-10T14:30:00+02:00 hello", arrival);
        assertThat(parsed.timestamp()).isEqualTo(LocalDateTime.of(2026, 6, 10, 12, 30));
        assertThat(parsed.line()).isEqualTo("hello");
    }

    @Test
    void parsesTimestampAndStreamFromSseDataWhenPresent() {
        LogTailService.LogLine line = LogTailService.parseLogLine(
                "2026-05-06T16:12:13Z stderr boom",
                LocalDateTime.of(2026, 5, 6, 18, 12, 13));

        assertEquals(LocalDateTime.of(2026, 5, 6, 16, 12, 13), line.timestamp());
        assertEquals("stderr", line.stream());
        assertEquals("boom", line.line());
    }

    @Test
    void usesArrivalTimeWhenNoTimestampExists() {
        LocalDateTime arrival = LocalDateTime.of(2026, 5, 6, 18, 12, 13);
        LogTailService.LogLine line = LogTailService.parseLogLine("plain log line", arrival);

        assertEquals(arrival, line.timestamp());
        assertEquals("stdout", line.stream());
        assertEquals("plain log line", line.line());
    }
}
