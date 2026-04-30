package dev.filipnikolov.vector.docker.controller;

import dev.filipnikolov.vector.docker.service.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/internal/apps")
@RequiredArgsConstructor
public class InternalLogController {

    private static final long HEARTBEAT_INTERVAL_SEC = 15;
    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "internal-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final DockerService dockerService;

    @GetMapping(value = "/{appName}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable String appName) {
        SseEmitter emitter = new SseEmitter(0L);

        if (!dockerService.isContainerRunning(appName)) {
            try {
                emitter.send(SseEmitter.event().name("done").data("container not running"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        // tail=0 means follow from now only; no historical lines sent
        Closeable stream = dockerService.streamContainerLogs(
                appName,
                0,
                line -> {
                    try {
                        emitter.send(SseEmitter.event().data(line));
                    } catch (Exception ignored) {}
                },
                emitter::completeWithError,
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                    } catch (Exception ignored) {}
                    emitter.complete();
                });

        ScheduledFuture<?> heartbeat = HEARTBEAT_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().comment("keepalive"));
                    } catch (Exception ignored) {}
                },
                HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

        Runnable closeAll = () -> {
            heartbeat.cancel(false);
            try { stream.close(); } catch (Exception ignored) {}
        };
        emitter.onCompletion(closeAll);
        emitter.onTimeout(closeAll);
        emitter.onError(e -> closeAll.run());

        return emitter;
    }
}
