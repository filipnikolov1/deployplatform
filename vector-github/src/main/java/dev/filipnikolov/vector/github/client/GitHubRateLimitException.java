package dev.filipnikolov.vector.github.client;

/** Thrown when GitHub returns HTTP 403 with X-RateLimit-Remaining: 0. */
public class GitHubRateLimitException extends GitHubException {

    public GitHubRateLimitException(String message) {
        super(message);
    }
}
