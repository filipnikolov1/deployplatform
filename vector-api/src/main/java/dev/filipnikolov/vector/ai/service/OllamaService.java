package dev.filipnikolov.vector.ai.service;

/**
 * Facade over the Ollama HTTP API. Hides the RestClient, request/response
 * shapes, and error-to-exception mapping from callers.
 */
public interface OllamaService {

    /**
     * Sends a raw prompt to the configured model and returns the model's
     * completion as a plain string.
     *
     * @param prompt the prompt text
     * @return the model's completion
     * @throws dev.filipnikolov.vector.ai.exception.AiUnavailableException
     *         if Ollama is unreachable or returns an unexpected error
     * @throws dev.filipnikolov.vector.ai.exception.AiNotReadyException
     *         if the model is not pulled or still loading
     */
    String ask(String prompt);

    /**
     * Builds a DevOps-focused analysis prompt from the given log content and
     * returns the model's 2–3 sentence explanation.
     *
     * <p>The log content is truncated to the last ~8 KB before being embedded
     * in the prompt, to stay within the model's context window.
     *
     * @param logContent the raw container logs (can be multi-line)
     * @return the model's analysis
     */
    String analyzeLog(String logContent);
}
