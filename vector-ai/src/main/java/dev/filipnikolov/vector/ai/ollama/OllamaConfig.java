package dev.filipnikolov.vector.ai.ollama;

public record OllamaConfig(String baseUrl, String model, int timeoutSeconds) {}
