package dev.filipnikolov.vector.connect.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

/**
 * Placeholder until Task 19 wires the real build-event deploy pipeline. Routing from
 * {@link dev.filipnikolov.vector.deployhook.controller.GitHubWebhookController} lands here for
 * now; this task only proves the three event types reach the stub with parsed payloads.
 */
@Service
public class NoOpBuildEventService implements BuildEventService {

    @Override
    public void handlePush(JsonNode payload) {
    }

    @Override
    public void handleWorkflowRun(JsonNode payload) {
    }

    @Override
    public void handleWorkflowJob(JsonNode payload) {
    }
}
