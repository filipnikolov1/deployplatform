package dev.filipnikolov.vector.analyzer.crash;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses stack-trace top-frame info out of a stream of log lines.
 *
 * Supports the most common formats portfolio apps emit:
 *   - JVM: "at com.foo.Bar.method(Bar.java:42)"
 *   - Node:  "at Object.method (/path/file.js:42:10)" or "(/path/file.js:42:10)"
 *   - Python: 'File "/path/file.py", line 42, in function'
 */
public final class StackTraceParser {

    private static final Pattern JVM_FRAME =
            Pattern.compile("^\\s*at\\s+[^(]+\\(([^:)]+):(\\d+)\\)");

    private static final Pattern NODE_FRAME =
            Pattern.compile("^\\s*at\\s+.*?\\(?([^\\s(]+\\.(?:js|ts|mjs|cjs|tsx|jsx)):(\\d+)(?::\\d+)?\\)?\\s*$");

    private static final Pattern PYTHON_FRAME =
            Pattern.compile("^\\s*File\\s+\"([^\"]+\\.py)\",\\s+line\\s+(\\d+)");

    private static final Pattern GO_FRAME =
            Pattern.compile("^\\s*([^\\s:]+\\.go):(\\d+)");

    private StackTraceParser() {}

    /**
     * Returns the most likely crash-site frame from log lines ordered NEWEST-FIRST.
     *
     * JVM/Node/Go print the crash-site frame first (earliest), so scanning newest-first
     * walks the trace bottom-up: the last frame of the first contiguous frame-run is the
     * top frame. Python prints most-recent-call-last, so its first match IS the site.
     */
    public static Optional<Frame> findTopFrame(List<String> logLines) {
        Frame lastInRun = null;
        boolean inRun = false;
        for (String line : logLines) {
            if (line == null || line.isBlank()) {
                if (inRun) break;
                continue;
            }
            Matcher m;
            if ((m = PYTHON_FRAME.matcher(line)).find()) {
                return Optional.of(new Frame(m.group(1), parseLine(m.group(2))));
            }
            Frame f = null;
            if ((m = JVM_FRAME.matcher(line)).find()) {
                f = new Frame(m.group(1), parseLine(m.group(2)));
            } else if ((m = NODE_FRAME.matcher(line)).find()) {
                f = new Frame(m.group(1), parseLine(m.group(2)));
            } else if ((m = GO_FRAME.matcher(line)).find()) {
                f = new Frame(m.group(1), parseLine(m.group(2)));
            }
            if (f != null) {
                inRun = true;
                lastInRun = f;
            } else if (inRun) {
                break; // left the contiguous frame block — lastInRun is the top frame
            }
        }
        return Optional.ofNullable(lastInRun);
    }

    private static int parseLine(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    public record Frame(String file, int line) {}
}
