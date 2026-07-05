package dev.filipnikolov.vector.githubapp.service;

public interface GitHubAppWebhookAuthService {

    boolean isValidSignature(String payload, String signatureHeader);

    boolean registerDeliveryOnce(String deliveryGuid);
}
