package dev.filipnikolov.vector.monitoring.service.impl;

import dev.filipnikolov.vector.monitoring.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final long ALERT_COOLDOWN_MS = 15 * 60 * 1000L;

    private final RestClient restClient;
    private final String fromEmail;
    private final String toEmail;
    private final String apiKey;

    private final ConcurrentHashMap<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    public NotificationServiceImpl(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from:Vector <onboarding@resend.dev>}") String fromEmail,
            @Value("${resend.to:}") String toEmail) {

        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.toEmail = toEmail;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    @Async
    public void sendDownAlert(String appName) {
        if (!claimAlertSlot(appName)) {
            log.debug("Down alert suppressed (cooldown active) for: {}", appName);
            return;
        }
        send(appName,
                "Vector Alert: " + appName + " is down",
                "The application '" + appName + "' is not responding and has been marked as DOWN.");
    }

    @Override
    @Async
    public void sendCrashedAlert(String appName) {
        if (!claimAlertSlot(appName)) {
            log.debug("Crash alert suppressed (cooldown active) for: {}", appName);
            return;
        }
        send(appName,
                "Vector Alert: " + appName + " crashed",
                "The application '" + appName + "' container stopped unexpectedly and has been marked as DOWN.");
    }

    @Override
    @Async
    public void sendRecoveredAlert(String appName) {
        lastAlertAt.remove(appName);
        send(appName,
                "Vector: " + appName + " recovered",
                "The application '" + appName + "' is back up and running.");
    }

    @Override
    @Async
    public void sendDeployFailedAlert(String appName) {
        send(appName,
                "Vector Alert: " + appName + " deploy failed",
                "A deployment for '" + appName + "' has failed. Check the event log for details.");
    }

    private boolean claimAlertSlot(String appName) {
        long now = System.currentTimeMillis();
        Long prev = lastAlertAt.get(appName);
        if (prev != null && now - prev < ALERT_COOLDOWN_MS) return false;
        lastAlertAt.put(appName, now);
        return true;
    }

    private void send(String appName, String subject, String text) {
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
                            "subject", subject,
                            "text", text
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent alert '{}' for: {}", subject, appName);
        } catch (Exception e) {
            log.error("Failed to send alert for {}: {}", appName, e.getMessage());
        }
    }
}
