package dev.filipnikolov.vector.updater;

import dev.filipnikolov.vector.updater.dto.UpdateRequest;
import dev.filipnikolov.vector.updater.dto.UpdateResponse;
import dev.filipnikolov.vector.updater.dto.UpdaterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class UpdaterClient {

    private static final Logger log = LoggerFactory.getLogger(UpdaterClient.class);

    private final RestClient restClient;

    public UpdaterClient(
            @Value("${updater.base-url:http://vector-updater:8080}") String baseUrl,
            @Value("${updater.auth.token:}") String authToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));

        RestClient.Builder builder = RestClient.builder().requestFactory(factory).baseUrl(baseUrl);
        if (authToken != null && !authToken.isBlank()) {
            builder.defaultHeader("X-Updater-Auth", authToken);
        }
        this.restClient = builder.build();
    }

    public boolean health() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Updater health check failed: {}", e.getMessage());
            return false;
        }
    }

    public UpdateResponse update(String service, String image) {
        try {
            UpdateResponse resp = restClient.post()
                    .uri("/update")
                    .body(new UpdateRequest(service, image))
                    .retrieve()
                    .body(UpdateResponse.class);
            if (resp == null) {
                return new UpdateResponse("error", null, "empty response from updater");
            }
            return resp;
        } catch (Exception e) {
            log.error("Updater update call failed: {}", e.getMessage(), e);
            return new UpdateResponse("error", null, e.getMessage());
        }
    }

    public UpdaterStatus status(String service) {
        try {
            return restClient.get()
                    .uri("/status/{service}", service)
                    .retrieve()
                    .body(UpdaterStatus.class);
        } catch (Exception e) {
            log.warn("Updater status fetch failed for {}: {}", service, e.getMessage());
            return null;
        }
    }
}
