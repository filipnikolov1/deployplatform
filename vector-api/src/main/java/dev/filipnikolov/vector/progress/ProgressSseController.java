package dev.filipnikolov.vector.progress;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class ProgressSseController {

    private final ProgressHub progressHub;

    @GetMapping(value = "/{operationId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable String operationId) {
        return progressHub.subscribe(operationId);
    }
}
