package dev.filipnikolov.vector.docker.controller;

import dev.filipnikolov.vector.docker.service.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class RuntimeLogController {

    private static final int TAIL_LINES = 500;
    private static final long HEARTBEAT_INTERVAL_SEC = 15;
    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final DockerService dockerService;

    @GetMapping(value = "/{appName}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable String appName) {
        SseEmitter emitter = new SseEmitter(0L);

        if (!dockerService.isContainerRunning(appName)) {
            try {
                emitter.send(SseEmitter.event().reconnectTime(60_000L).comment("container stopped"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        Closeable stream = dockerService.streamContainerLogs(
                appName,
                TAIL_LINES,
                line -> {
                    try {
                        emitter.send(SseEmitter.event().data(line));
                    } catch (Exception ignored) {
                    }
                },
                emitter::completeWithError,
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                    } catch (Exception ignored) {
                    }
                    emitter.complete();
                });

        ScheduledFuture<?> heartbeat = HEARTBEAT_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().comment("keepalive"));
                    } catch (Exception ignored) {
                    }
                },
                HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

        Runnable closeAll = () -> {
            heartbeat.cancel(false);
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        };
        emitter.onCompletion(closeAll);
        emitter.onTimeout(closeAll);
        emitter.onError(e -> closeAll.run());

        return emitter;
    }

    private static final int DOWNLOAD_TAIL_LINES = 10_000;
    private static final int MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;

    @GetMapping(value = "/{appName}/logs/runtime/download", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<byte[]> downloadLogs(@PathVariable String appName) {
        List<String> lines = dockerService.getContainerLogs(appName, DOWNLOAD_TAIL_LINES);
        String joined = String.join("\n", lines);
        byte[] body = joined.getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_DOWNLOAD_BYTES) {
            body = Arrays.copyOf(body, MAX_DOWNLOAD_BYTES);
        }
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + appName + "-logs-" + ts + ".txt\"")
                .body(body);
    }
}
