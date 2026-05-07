package dev.filipnikolov.vector.deployhook.idempotency;

/**
 * Guards against webhook replay attacks by tracking signatures that have
 * already been accepted within the replay window.
 */
public interface WebhookIdempotencyService {

    /**
     * Attempts to register the given signature as seen for the first time.
     *
     * @param signature the full signature header value (e.g. "sha256=abc123...")
     * @return {@code true} if this is the first time this signature has been seen,
     *         {@code false} if it was already registered (replay detected)
     */
    boolean tryRegister(String signature);
}
