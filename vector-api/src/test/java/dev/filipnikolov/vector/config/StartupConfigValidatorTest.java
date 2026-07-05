package dev.filipnikolov.vector.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupConfigValidatorTest {

    private StartupConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StartupConfigValidator();
        ReflectionTestUtils.setField(validator, "apiKey", "generated-api-key");
        ReflectionTestUtils.setField(validator, "encryptionKey",
                Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(validator, "deployHookSecret", "generated-hook-secret");
        ReflectionTestUtils.setField(validator, "aiProvider", "gemini");
        ReflectionTestUtils.setField(validator, "geminiApiKey", "user-gemini-key");
        ReflectionTestUtils.setField(validator, "selfHosted", false);
        ReflectionTestUtils.setField(validator, "updaterAuthToken", "");
    }

    @Test
    void passesWithGeneratedSecretsPresent() {
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void failsWhenGeminiProviderHasBlankKey() {
        ReflectionTestUtils.setField(validator, "geminiApiKey", "");
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VECTOR_GEMINI_API_KEY");
    }

    @Test
    void ollamaProviderDoesNotRequireGeminiKey() {
        ReflectionTestUtils.setField(validator, "aiProvider", "ollama");
        ReflectionTestUtils.setField(validator, "geminiApiKey", "");
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void selfHostedRequiresUpdaterTokenWithCorrectEnvName() {
        ReflectionTestUtils.setField(validator, "selfHosted", true);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VECTOR_UPDATER_AUTH_TOKEN");
    }
}
