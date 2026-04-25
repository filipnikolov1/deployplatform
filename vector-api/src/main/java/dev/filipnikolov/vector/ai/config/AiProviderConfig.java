package dev.filipnikolov.vector.ai.config;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.gemini.GeminiConfig;
import dev.filipnikolov.vector.ai.gemini.GeminiProvider;
import dev.filipnikolov.vector.ai.ollama.OllamaConfig;
import dev.filipnikolov.vector.ai.ollama.OllamaProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {

    @Bean
    @ConditionalOnProperty(name = "vector.ai.provider", havingValue = "gemini", matchIfMissing = true)
    public AiProvider geminiProvider(
            @Value("${vector.ai.gemini.api-key:}") String apiKey,
            @Value("${vector.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${vector.ai.gemini.timeout-seconds:30}") int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "VECTOR_GEMINI_API_KEY is required when vector.ai.provider=gemini. " +
                    "Set the environment variable or switch to vector.ai.provider=ollama.");
        }
        return new GeminiProvider(new GeminiConfig(apiKey, model, timeoutSeconds));
    }

    @Bean
    @ConditionalOnProperty(name = "vector.ai.provider", havingValue = "ollama")
    public AiProvider ollamaProvider(
            @Value("${vector.ai.base-url:http://localhost:11434}") String baseUrl,
            @Value("${vector.ai.model:llama3.2:3b}") String model,
            @Value("${vector.ai.request-timeout-seconds:120}") int timeoutSeconds) {
        return new OllamaProvider(new OllamaConfig(baseUrl, model, timeoutSeconds));
    }
}
