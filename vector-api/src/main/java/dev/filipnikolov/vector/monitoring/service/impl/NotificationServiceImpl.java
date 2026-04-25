package dev.filipnikolov.vector.monitoring.service.impl;

import dev.filipnikolov.vector.monitoring.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends email alerts via the Resend API.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final RestClient restClient;
    private final String fromEmail;
    private final String toEmail;
    private final String apiKey;

    public NotificationServiceImpl(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from:Launchpad <onboarding@resend.dev>}") String fromEmail,
            @Value("${resend.to:}") String toEmail) {

        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.toEmail = toEmail;

        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public void sendDownAlert(String appName) {
        if (toEmail.isEmpty() || apiKey.isEmpty()) {
            log.warn("Resend not fully configured (to='{}', api-key={}). Skipping alert for: {}",
                    toEmail, apiKey.isEmpty() ? "missing" : "set", appName);
            return;
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .body(Map.of(
                            "from", fromEmail,
                            "to", toEmail,
                            "subject", "Launchpad Alert: " + appName + " is down",
                            "text", "The application '" + appName + "' is not responding and has been marked as DOWN."
                    ))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent down alert for: {}", appName);
        } catch (Exception e) {
            log.error("Failed to send alert for {}: {}", appName, e.getMessage());
        }
    }
}
