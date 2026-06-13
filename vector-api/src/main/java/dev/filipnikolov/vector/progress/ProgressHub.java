package dev.filipnikolov.vector.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ProgressHub {

    private static final Logger log = LoggerFactory.getLogger(ProgressHub.class);
    private static final int RING_SIZE = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ConcurrentHashMap<String, OperationState> ops = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "progress-evict");
        t.setDaemon(true);
        return t;
    });

    public void start(String operationId) {
        ops.put(operationId, new OperationState());
    }

    public void emit(String operationId, ProgressFrame frame) {
        OperationState state = ops.get(operationId);
        if (state == null) return;

        synchronized (state.buffer) {
            state.buffer.add(frame);
            if (state.buffer.size() > RING_SIZE) {
                state.buffer.remove(0);
            }
        }

        String json;
        try {
            json = MAPPER.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("Could not serialize ProgressFrame for op {}: {}", operationId, e.getMessage());
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : state.emitters) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(json));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        state.emitters.removeAll(dead);
    }

    public void end(String operationId) {
        OperationState state = ops.get(operationId);
        if (state == null) return;

        synchronized (state) {
            state.done = true;
            for (SseEmitter emitter : state.emitters) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
            state.emitters.clear();
        }

        scheduler.schedule(() -> ops.remove(operationId), 30, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(String operationId) {
        OperationState state = ops.get(operationId);
        SseEmitter emitter = new SseEmitter(0L);

        if (state == null) {
            emitter.complete();
            return emitter;
        }

        // Replay buffered frames
        List<ProgressFrame> snapshot;
        synchronized (state.buffer) {
            snapshot = new ArrayList<>(state.buffer);
        }
        for (ProgressFrame frame : snapshot) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(MAPPER.writeValueAsString(frame)));
            } catch (Exception e) {
                emitter.completeWithError(e);
                return emitter;
            }
        }

        synchronized (state) {
            if (state.done) {
                emitter.complete();
                return emitter;
            }
            state.emitters.add(emitter);
        }
        emitter.onCompletion(() -> state.emitters.remove(emitter));
        emitter.onTimeout(() -> state.emitters.remove(emitter));
        emitter.onError(ignored -> state.emitters.remove(emitter));

        return emitter;
    }

    private static class OperationState {
        volatile boolean done;
        final List<ProgressFrame> buffer = new ArrayList<>(RING_SIZE);
        final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    }
}
