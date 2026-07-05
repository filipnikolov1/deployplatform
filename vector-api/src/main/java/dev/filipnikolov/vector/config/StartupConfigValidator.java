package dev.filipnikolov.vector.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Validates required configuration at startup and fails fast with a clear,
 * actionable error message listing ALL missing/invalid properties at once.
 */
@Component
public class StartupConfigValidator {

    private static final List<String> SUPPORTED_AI_PROVIDERS = List.of("gemini", "ollama");

    @Value("${app.api-key:}")
    private String apiKey;

    @Value("${encryption.key:}")
    private String encryptionKey;

    @Value("${deploy.hook.secret:}")
    private String deployHookSecret;

    @Value("${vector.ai.provider:}")
    private String aiProvider;

    @Value("${vector.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${vector.self-hosted:false}")
    private boolean selfHosted;

    @Value("${updater.auth.token:}")
    private String updaterAuthToken;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();

        if (apiKey == null || apiKey.isBlank()) {
            errors.add("  - app.api-key (env: VECTOR_API_KEY) must not be blank");
        }

        if (encryptionKey == null || encryptionKey.isBlank()) {
            errors.add("  - encryption.key (env: VECTOR_ENCRYPTION_KEY) must not be blank");
        } else {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
                if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                    errors.add("  - encryption.key (env: VECTOR_ENCRYPTION_KEY) must Base64-decode to " +
                            "16, 24, or 32 bytes (AES-128/192/256); got " + keyBytes.length + " bytes");
                }
            } catch (IllegalArgumentException e) {
                errors.add("  - encryption.key (env: VECTOR_ENCRYPTION_KEY) is not valid Base64");
            }
        }

        if (deployHookSecret == null || deployHookSecret.isBlank()) {
            errors.add("  - deploy.hook.secret (env: VECTOR_DEPLOY_HOOK_SECRET) must not be blank");
        }

        if (aiProvider == null || aiProvider.isBlank()) {
            errors.add("  - vector.ai.provider (env: VECTOR_AI_PROVIDER) must not be blank; " +
                    "supported values: " + SUPPORTED_AI_PROVIDERS);
        } else if (!SUPPORTED_AI_PROVIDERS.contains(aiProvider.toLowerCase())) {
            errors.add("  - vector.ai.provider (env: VECTOR_AI_PROVIDER) has unsupported value '" +
                    aiProvider + "'; supported values: " + SUPPORTED_AI_PROVIDERS);
        }

        if ("gemini".equalsIgnoreCase(aiProvider) && (geminiApiKey == null || geminiApiKey.isBlank())) {
            errors.add("  - vector.ai.gemini.api-key (env: VECTOR_GEMINI_API_KEY) must not be blank " +
                    "when vector.ai.provider=gemini (the default)");
        }

        if (selfHosted && (updaterAuthToken == null || updaterAuthToken.isBlank())) {
            errors.add("  - updater.auth.token (env: VECTOR_UPDATER_AUTH_TOKEN) must not be blank " +
                    "when vector.self-hosted=true (env: VECTOR_SELF_HOSTED=true)");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Vector API startup configuration is invalid. Fix the following:\n" +
                    String.join("\n", errors));
        }
    }
}
