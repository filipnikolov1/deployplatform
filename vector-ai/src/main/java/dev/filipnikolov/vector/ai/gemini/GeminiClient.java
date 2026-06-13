package dev.filipnikolov.vector.ai.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.ai.AiProviderException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class GeminiClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final GeminiConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiClient(GeminiConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String generateContent(String prompt) {
        String url = String.format(ENDPOINT, config.model());
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                throw new AiProviderException("Gemini rate limit exceeded, try again shortly");
            }
            if (response.statusCode() != 200) {
                throw new AiProviderException("Gemini API error: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates")
                    .path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new AiProviderException("Gemini returned empty response");
            }
            return text;
        } catch (AiProviderException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new AiProviderException("Gemini request timed out after " + config.timeoutSeconds() + "s", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AiProviderException("Gemini request failed: " + e.getMessage(), e);
        }
    }

    public boolean hasApiKey() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }
}
