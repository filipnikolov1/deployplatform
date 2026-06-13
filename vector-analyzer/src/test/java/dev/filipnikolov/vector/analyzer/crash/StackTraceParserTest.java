package dev.filipnikolov.vector.analyzer.crash;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StackTraceParserTest {

    @Test
    void jvmTraceReturnsTopFrameNotBottom() {
        // Printed order (oldest→newest): exception, Crash.java:42, Main.java:10.
        // Input is newest-first, like fetchCrashLogWindow returns.
        List<String> newestFirst = List.of(
                "    at com.foo.Main.main(Main.java:10)",
                "    at com.foo.Crash.site(Crash.java:42)",
                "Exception in thread \"main\" java.lang.NullPointerException");
        var frame = StackTraceParser.findTopFrame(newestFirst).orElseThrow();
        assertThat(frame.file()).isEqualTo("Crash.java");
        assertThat(frame.line()).isEqualTo(42);
    }

    @Test
    void causedByChainReturnsRootCauseTopFrame() {
        List<String> newestFirst = List.of(
                "    at com.foo.Dao.query(Dao.java:88)",
                "Caused by: java.sql.SQLException: boom",
                "    at com.foo.Main.main(Main.java:10)",
                "    at com.foo.Service.run(Service.java:5)",
                "Exception in thread \"main\" java.lang.RuntimeException");
        var frame = StackTraceParser.findTopFrame(newestFirst).orElseThrow();
        assertThat(frame.file()).isEqualTo("Dao.java");
        assertThat(frame.line()).isEqualTo(88);
    }

    @Test
    void nodeTraceReturnsTopFrame() {
        List<String> newestFirst = List.of(
                "    at processTicksAndRejections (node:internal/process/task_queues.js:95:5)",
                "    at Object.handler (/app/src/index.js:42:10)",
                "TypeError: Cannot read properties of undefined");
        var frame = StackTraceParser.findTopFrame(newestFirst).orElseThrow();
        assertThat(frame.file()).isEqualTo("/app/src/index.js");
        assertThat(frame.line()).isEqualTo(42);
    }

    @Test
    void pythonTraceReturnsLastFileLine() {
        // Python prints most-recent-call-LAST: crash site is the last File line printed,
        // i.e. the FIRST one encountered newest-first.
        List<String> newestFirst = List.of(
                "ValueError: boom",
                "  File \"/app/handler.py\", line 42, in handle",
                "  File \"/app/main.py\", line 10, in main",
                "Traceback (most recent call last):");
        var frame = StackTraceParser.findTopFrame(newestFirst).orElseThrow();
        assertThat(frame.file()).isEqualTo("/app/handler.py");
        assertThat(frame.line()).isEqualTo(42);
    }

    @Test
    void noFramesReturnsEmpty() {
        assertThat(StackTraceParser.findTopFrame(List.of("plain log line"))).isEmpty();
    }
}
