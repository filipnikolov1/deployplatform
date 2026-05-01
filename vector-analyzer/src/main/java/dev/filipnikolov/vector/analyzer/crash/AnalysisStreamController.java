package dev.filipnikolov.vector.analyzer.crash;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/analyzer/apps/{appName}")
public class AnalysisStreamController {

    private final AnalysisStreamBroadcaster broadcaster;

    public AnalysisStreamController(AnalysisStreamBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String appName) {
        return broadcaster.register(appName);
    }
}
