package com.filipnikolov.launchpad.webhook.auth.service.impl;

import com.filipnikolov.launchpad.webhook.auth.service.WebhookAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class WebhookAuthServiceImpl implements WebhookAuthService {

    @Value("${github.webhook.secret}")
    private String secret;

    @Override
    public boolean isValidSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            String expectedSignature = "sha256=" + hexString;
            return expectedSignature.equals(signatureHeader);

        } catch (Exception e) {
            return false;
        }
    }
}
