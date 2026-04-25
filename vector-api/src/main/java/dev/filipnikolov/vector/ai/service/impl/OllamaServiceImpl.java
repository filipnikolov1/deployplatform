package dev.filipnikolov.vector.ai.service.impl;

import dev.filipnikolov.vector.ai.exception.AiNotReadyException;
import dev.filipnikolov.vector.ai.exception.AiUnavailableException;
import dev.filipnikolov.vector.ai.model.OllamaRequest;
import dev.filipnikolov.vector.ai.model.OllamaResponse;
import dev.filipnikolov.vector.ai.service.OllamaService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

@Service
public class OllamaServiceImpl implements OllamaService {

    private static final int MAX_LOG_BYTES = 8 * 1024;

    private final RestClient restClient;
    private final String model;

    public OllamaServiceImpl(
            @Qualifier("ollamaRestClient") RestClient restClient,
            @Value("${vector.ai.model}") String model) {
        this.restClient = restClient;
        this.model = model;
    }

    @Override
    public String ask(String prompt) {
        OllamaRequest request = new OllamaRequest(model, prompt, false);
        try {
            OllamaResponse response = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (body.contains("not found")) {
                            throw new AiNotReadyException("Model not ready, try again shortly");
                        }
                        throw new AiUnavailableException("AI service error");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new AiUnavailableException("AI service error");
                    })
                    .body(OllamaResponse.class);

            if (response == null || response.response() == null) {
                throw new AiUnavailableException("AI service returned empty response");
            }
            return response.response();
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new AiNotReadyException("Model not ready, try again shortly", e);
            }
            throw new AiUnavailableException("AI service unavailable", e);
        }
    }

    @Override
    public String analyzeLog(String logContent) {
        String truncated = truncateToTailBytes(logContent, MAX_LOG_BYTES);
        String prompt = "You are a DevOps assistant. Analyze this deployment log "
                + "and explain what went wrong in 2-3 sentences: " + truncated;
        return ask(prompt);
    }

    private static String truncateToTailBytes(String content, int maxBytes) {
        if (content == null) {
            return "";
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return content;
        }
        byte[] tail = new byte[maxBytes];
        System.arraycopy(bytes, bytes.length - maxBytes, tail, 0, maxBytes);
        return new String(tail, StandardCharsets.UTF_8);
    }
}
