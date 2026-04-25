package dev.filipnikolov.vector.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of Ollama's /api/generate response (stream=false).
 * Only captures the "response" field; other fields (model, done, metrics) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaResponse(String response) {
}
