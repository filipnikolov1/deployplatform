package dev.filipnikolov.vector.github.client;

/** Base exception for GitHub HTTP errors. */
public class GitHubException extends RuntimeException {

    public GitHubException(String message) {
        super(message);
    }

    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
