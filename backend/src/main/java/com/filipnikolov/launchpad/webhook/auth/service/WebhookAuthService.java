package com.filipnikolov.launchpad.webhook.auth.service;

/**
 * Verifies the authenticity of incoming GitHub webhook requests
 * using HMAC SHA-256 signature validation.
 */
public interface WebhookAuthService {

    /**
     * Validates the webhook payload against the X-Hub-Signature-256 header.
     * Computes HMAC SHA-256 of the payload using the shared secret and compares
     * it to the signature provided by GitHub.
     *
     * @param payload         the raw request body
     * @param signatureHeader the X-Hub-Signature-256 header value (e.g. "sha256=abc123...")
     * @return true if the signature is valid, false otherwise
     */
    boolean isValidSignature(String payload, String signatureHeader);
}
