package dev.filipnikolov.vector.ai;

public interface AiProvider {

    AiResponse analyze(AiRequest request) throws AiProviderException;

    String providerName();

    boolean isAvailable();
}
