package dev.filipnikolov.vector.progress;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressHubTest {

    @Test
    void subscribingToFinishedOperationCompletesImmediately() {
        ProgressHub hub = new ProgressHub();
        hub.start("op-1");
        hub.end("op-1");

        SseEmitter emitter = hub.subscribe("op-1"); // op still in the 30s eviction window

        // A completed emitter rejects further sends — proves subscribe() completed it.
        assertThatThrownBy(() -> emitter.send(SseEmitter.event().data("x")))
                .isInstanceOf(IllegalStateException.class);
    }
}
