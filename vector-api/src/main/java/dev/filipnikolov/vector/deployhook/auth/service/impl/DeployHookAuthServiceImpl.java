package dev.filipnikolov.vector.deployhook.auth.service.impl;

import dev.filipnikolov.vector.deployhook.auth.service.DeployHookAuthService;
import dev.filipnikolov.vector.deployhook.idempotency.WebhookIdempotencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class DeployHookAuthServiceImpl implements DeployHookAuthService {

    @Value("${deploy.hook.secret}")
    private String secret;

    private final WebhookIdempotencyService idempotencyService;

    public DeployHookAuthServiceImpl(WebhookIdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    public boolean isValidSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            String expectedSignature = "sha256=" + HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean registerSignatureOnce(String signatureHeader) {
        return idempotencyService.tryRegister(signatureHeader);
    }
}
