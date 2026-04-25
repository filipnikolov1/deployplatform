package dev.filipnikolov.vector.ai.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.AiProviderException;
import dev.filipnikolov.vector.ai.AiRequest;
import dev.filipnikolov.vector.ai.AiResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

public class OllamaProvider implements AiProvider {

    private final OllamaConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaProvider(OllamaConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public AiResponse analyze(AiRequest request) throws AiProviderException {
        Map<String, Object> body = Map.of(
                "model", config.model(),
                "prompt", request.prompt(),
                "stream", false
        );
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new AiProviderException("Ollama model not ready, try again shortly");
            }
            if (response.statusCode() != 200) {
                throw new AiProviderException("Ollama error: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("response").asText("");
            if (text.isBlank()) {
                throw new AiProviderException("Ollama returned empty response");
            }
            return new AiResponse(text, providerName());
        } catch (AiProviderException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new AiProviderException("Ollama request timed out after " + config.timeoutSeconds() + "s", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AiProviderException("Ollama request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "ollama-" + config.model();
    }

    @Override
    public boolean isAvailable() {
        return config.baseUrl() != null && !config.baseUrl().isBlank();
    }
}
