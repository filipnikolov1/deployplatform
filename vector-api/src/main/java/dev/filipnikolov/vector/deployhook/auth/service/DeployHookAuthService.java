package dev.filipnikolov.vector.deployhook.auth.service;

/**
 * Verifies the authenticity of incoming deploy-hook requests
 * using HMAC SHA-256 signature validation.
 */
public interface DeployHookAuthService {

    /**
     * Validates the deploy-hook payload against the X-Signature-256 header.
     * Computes HMAC SHA-256 of the payload using the shared secret and compares
     * it to the signature provided by the caller (GitHub Actions).
     *
     * @param payload         the raw request body
     * @param signatureHeader the X-Signature-256 header value (e.g. "sha256=abc123...")
     * @return true if the signature is valid, false otherwise
     */
    boolean isValidSignature(String payload, String signatureHeader);

    /**
     * Registers the signature as seen. Must only be called after
     * {@link #isValidSignature} has returned {@code true}.
     *
     * @param signatureHeader the X-Signature-256 header value
     * @return {@code true} if this is the first time this signature has been seen
     *         (request should proceed), {@code false} if it is a replay (reject with 409)
     */
    boolean registerSignatureOnce(String signatureHeader);
}
