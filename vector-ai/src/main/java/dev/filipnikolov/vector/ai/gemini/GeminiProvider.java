package dev.filipnikolov.vector.ai.gemini;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.AiProviderException;
import dev.filipnikolov.vector.ai.AiRequest;
import dev.filipnikolov.vector.ai.AiResponse;

public class GeminiProvider implements AiProvider {

    private final GeminiClient client;
    private final String model;

    public GeminiProvider(GeminiConfig config) {
        this.client = new GeminiClient(config);
        this.model = config.model();
    }

    @Override
    public AiResponse analyze(AiRequest request) throws AiProviderException {
        String text = client.generateContent(request.prompt());
        return new AiResponse(text, providerName());
    }

    @Override
    public String providerName() {
        // The configured model already starts with "gemini-" (e.g. "gemini-2.5-flash"),
        // so stamping it through unchanged avoids "gemini-gemini-2.5-flash" in metadata.
        return model;
    }

    @Override
    public boolean isAvailable() {
        return client.hasApiKey();
    }
}
