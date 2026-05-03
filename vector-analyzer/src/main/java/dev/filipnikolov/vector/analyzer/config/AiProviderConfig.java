package dev.filipnikolov.vector.analyzer.config;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.gemini.GeminiConfig;
import dev.filipnikolov.vector.ai.gemini.GeminiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Bean
    @ConditionalOnProperty(name = "vector.ai.provider", havingValue = "gemini", matchIfMissing = true)
    public AiProvider geminiProvider(
            @Value("${vector.ai.gemini.api-key:}") String apiKey,
            @Value("${vector.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${vector.ai.gemini.timeout-seconds:30}") int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("VECTOR_GEMINI_API_KEY not set — AI narration will mark crashes UNAVAILABLE");
        }
        return new GeminiProvider(new GeminiConfig(apiKey, model, timeoutSeconds));
    }
}
