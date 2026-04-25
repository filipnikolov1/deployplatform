package com.filipnikolov.launchpad.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the RestClient used to talk to Ollama.
 *
 * <p>Timeouts are deliberately long: a cold model load on a 3B model can take
 * 5–30 seconds, and long generations push past default HTTP client timeouts.
 * Connect timeout stays short so an unreachable Ollama fails fast.
 */
@Configuration
public class OllamaRestClientConfig {

    @Bean
    public RestClient ollamaRestClient(
            @Value("${launchpad.ai.base-url}") String baseUrl,
            @Value("${launchpad.ai.request-timeout-seconds}") int requestTimeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(requestTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
