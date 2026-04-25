package dev.filipnikolov.vector.ai.service.impl;

import dev.filipnikolov.vector.ai.exception.AiNotReadyException;
import dev.filipnikolov.vector.ai.exception.AiUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaServiceImplTest {

    private HttpServer server;
    private OllamaServiceImpl service;
    private AtomicReference<String> capturedRequestBody;
    private volatile int responseStatus;
    private volatile String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        capturedRequestBody = new AtomicReference<>();
        responseStatus = 200;
        responseBody = "{\"response\":\"hello\"}";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            capturedRequestBody.set(new String(reqBytes, StandardCharsets.UTF_8));
            byte[] resBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, resBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resBytes);
            }
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        service = new OllamaServiceImpl(restClient, "llama3.2:3b");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ask_returnsModelResponse_onHappyPath() {
        responseStatus = 200;
        responseBody = "{\"response\":\"the sky is blue\"}";

        String result = service.ask("why is the sky blue?");

        assertThat(result).isEqualTo("the sky is blue");
        assertThat(capturedRequestBody.get())
                .contains("\"model\":\"llama3.2:3b\"")
                .contains("\"prompt\":\"why is the sky blue?\"")
                .contains("\"stream\":false");
    }

    @Test
    void ask_throwsAiNotReady_whenOllamaReturns404ModelNotFound() {
        responseStatus = 404;
        responseBody = "{\"error\":\"model 'llama3.2:3b' not found, try pulling it first\"}";

        assertThatThrownBy(() -> service.ask("hi"))
                .isInstanceOf(AiNotReadyException.class)
                .hasMessageContaining("Model not ready");
    }

    @Test
    void ask_throwsAiUnavailable_whenOllamaReturns500() {
        responseStatus = 500;
        responseBody = "{\"error\":\"internal\"}";

        assertThatThrownBy(() -> service.ask("hi"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("AI service error");
    }

    @Test
    void ask_throwsAiUnavailable_whenOllamaConnectionRefused() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(1));
        RestClient deadClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:1")
                .requestFactory(factory)
                .build();
        OllamaServiceImpl deadService = new OllamaServiceImpl(deadClient, "llama3.2:3b");

        assertThatThrownBy(() -> deadService.ask("hi"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("AI service unavailable");
    }

    @Test
    void analyzeLog_sendsDevOpsPromptAndReturnsResponse() {
        responseStatus = 200;
        responseBody = "{\"response\":\"Your container crashed because of X.\"}";

        String result = service.analyzeLog("NullPointerException at line 42");

        assertThat(result).isEqualTo("Your container crashed because of X.");
        assertThat(capturedRequestBody.get())
                .contains("You are a DevOps assistant")
                .contains("NullPointerException at line 42")
                .contains("2-3 sentences");
    }

    @Test
    void analyzeLog_truncatesOversizedLogsToTail8KB() {
        responseStatus = 200;
        responseBody = "{\"response\":\"ok\"}";

        // Build a 20 KB log ending in a unique marker
        StringBuilder sb = new StringBuilder();
        sb.append("A".repeat(20_000));
        sb.append("_TAIL_MARKER_");
        String bigLog = sb.toString();

        service.analyzeLog(bigLog);

        String capturedBody = capturedRequestBody.get();
        // The tail marker must be present
        assertThat(capturedBody).contains("_TAIL_MARKER_");
        // The serialized prompt must not contain more than ~8 KB of the original 'A's
        int aRunInBody = capturedBody.length() - capturedBody.replace("A", "").length();
        assertThat(aRunInBody).isLessThanOrEqualTo(8_192);
    }
}
