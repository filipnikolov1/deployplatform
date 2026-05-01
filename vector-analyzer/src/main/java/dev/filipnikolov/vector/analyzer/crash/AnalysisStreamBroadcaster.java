package dev.filipnikolov.vector.analyzer.crash;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out for analyzer-side SSE events. Frontend subscribes per-app via
 * GET /api/analyzer/apps/{appName}/stream and receives events as they happen.
 */
@Component
public class AnalysisStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(AnalysisStreamBroadcaster.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, List<SseEmitter>> emittersByApp = new ConcurrentHashMap<>();

    public SseEmitter register(String appName) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout — caller manages lifecycle
        emittersByApp.computeIfAbsent(appName, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remove = () -> {
            List<SseEmitter> list = emittersByApp.get(appName);
            if (list != null) list.remove(emitter);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void publish(String appName, String eventName, Object payload) {
        List<SseEmitter> list = emittersByApp.get(appName);
        if (list == null || list.isEmpty()) return;

        String body;
        try {
            body = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize SSE payload: {}", e.getMessage());
            return;
        }

        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(eventName).data(body));
            } catch (Exception ex) {
                e.completeWithError(ex);
            }
        }
    }
}
