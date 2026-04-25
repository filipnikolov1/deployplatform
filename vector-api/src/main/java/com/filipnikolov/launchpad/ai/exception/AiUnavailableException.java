package com.filipnikolov.launchpad.ai.exception;

/**
 * Thrown when the Ollama backend is unreachable (connect refused, DNS fail,
 * unknown server error). Mapped to HTTP 503 by GlobalExceptionHandler.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
