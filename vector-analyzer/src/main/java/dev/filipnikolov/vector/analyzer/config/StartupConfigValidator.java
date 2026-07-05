package dev.filipnikolov.vector.analyzer.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    @Value("${vector.ai.provider:}")
    private String aiProvider;

    @Value("${vector.ai.gemini.api-key:}")
    private String geminiApiKey;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();

        if (apiKey == null || apiKey.isBlank()) {
            errors.add("  - app.api-key (env: VECTOR_API_KEY) must not be blank");
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

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Vector Analyzer startup configuration is invalid. Fix the following:\n" +
                    String.join("\n", errors));
        }
    }
}
