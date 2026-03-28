package com.filipnikolov.launchpad.docker.buildlog.controller;

import com.filipnikolov.launchpad.docker.buildlog.service.BuildLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class BuildLogController {

    private final BuildLogService buildLogService;

    /**
     * Opens an SSE stream for real-time build logs. Subscribe before triggering
     * a deploy to receive pull progress, container start, and routing info.
     */
    @GetMapping(value = "/{appName}/logs/build", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBuildLogs(@PathVariable String appName) {
        return buildLogService.subscribe(appName);
    }
}
