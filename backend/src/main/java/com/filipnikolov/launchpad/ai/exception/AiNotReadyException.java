package com.filipnikolov.launchpad.ai.exception;

/**
 * Thrown when the requested model is not pulled yet, or is mid-load and the
 * request timed out. Mapped to HTTP 503 by GlobalExceptionHandler.
 */
public class AiNotReadyException extends RuntimeException {
    public AiNotReadyException(String message) {
        super(message);
    }

    public AiNotReadyException(String message, Throwable cause) {
        super(message, cause);
    }
}
