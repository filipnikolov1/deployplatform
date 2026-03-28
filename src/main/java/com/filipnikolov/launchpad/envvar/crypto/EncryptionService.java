package com.filipnikolov.launchpad.envvar.crypto;

/**
 * Handles symmetric encryption and decryption of sensitive data
 * using AES-256-GCM. Used to protect environment variable values at rest.
 */
public interface EncryptionService {

    /**
     * Encrypts a plaintext string. Each call produces a different ciphertext
     * due to random IV generation.
     *
     * @param plaintext the value to encrypt
     * @return Base64-encoded ciphertext (IV prepended)
     */
    String encrypt(String plaintext);

    /**
     * Decrypts a ciphertext string back to its original plaintext.
     *
     * @param ciphertext Base64-encoded ciphertext (as produced by {@link #encrypt})
     * @return the original plaintext value
     */
    String decrypt(String ciphertext);
}
