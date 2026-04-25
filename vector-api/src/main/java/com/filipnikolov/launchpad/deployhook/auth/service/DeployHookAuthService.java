package com.filipnikolov.launchpad.deployhook.auth.service;

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
}
