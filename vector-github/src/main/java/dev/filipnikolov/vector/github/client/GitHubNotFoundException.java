package dev.filipnikolov.vector.github.client;

/** Thrown when the GitHub API returns HTTP 404 for a resource. */
public class GitHubNotFoundException extends GitHubException {

    public GitHubNotFoundException(String message) {
        super(message);
    }
}
