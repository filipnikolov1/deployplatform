package com.filipnikolov.launchpad.docker.controller;

import com.filipnikolov.launchpad.docker.service.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class RuntimeLogController {

    private static final int TAIL_LINES = 500;

    private final DockerService dockerService;

    @GetMapping(value = "/{appName}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable String appName) {
        SseEmitter emitter = new SseEmitter(0L);

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
                emitter::complete);

        Runnable closeStream = () -> {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        };
        emitter.onCompletion(closeStream);
        emitter.onTimeout(closeStream);
        emitter.onError(e -> closeStream.run());

        return emitter;
    }
}
