package com.filipnikolov.launchpad.ai.model;

/**
 * Request body for POST /api/generate on the Ollama HTTP API.
 *
 * @param model  model tag, e.g. "llama3.2:3b"
 * @param prompt the user prompt
 * @param stream if false, the server returns a single JSON object on completion
 */
public record OllamaRequest(String model, String prompt, boolean stream) {
}
