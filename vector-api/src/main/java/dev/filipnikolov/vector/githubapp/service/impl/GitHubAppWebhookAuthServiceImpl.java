package dev.filipnikolov.vector.githubapp.service.impl;

import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.githubapp.service.GitHubAppWebhookAuthService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GitHubAppWebhookAuthServiceImpl implements GitHubAppWebhookAuthService {

    private static final int MAX_TRACKED_DELIVERIES = 1000;

    private final GitHubAppConfigService configService;
    private final Map<String, Boolean> seenDeliveries = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_TRACKED_DELIVERIES;
        }
    };

    public GitHubAppWebhookAuthServiceImpl(GitHubAppConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean isValidSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        return configService.resolve().map(credentials -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(credentials.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

                String expectedSignature = "sha256=" + HexFormat.of().formatHex(hash);
                return MessageDigest.isEqual(
                        expectedSignature.getBytes(StandardCharsets.UTF_8),
                        signatureHeader.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return false;
            }
        }).orElse(false);
    }

    @Override
    public synchronized boolean registerDeliveryOnce(String deliveryGuid) {
        if (deliveryGuid == null) {
            return true;
        }
        if (seenDeliveries.containsKey(deliveryGuid)) {
            return false;
        }
        seenDeliveries.put(deliveryGuid, Boolean.TRUE);
        return true;
    }
}
