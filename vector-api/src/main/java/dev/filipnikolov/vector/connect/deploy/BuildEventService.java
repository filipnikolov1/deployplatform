package dev.filipnikolov.vector.connect.deploy;

import com.fasterxml.jackson.databind.JsonNode;

public interface BuildEventService {

    void handlePush(JsonNode payload);

    void handleWorkflowRun(JsonNode payload);

    void handleWorkflowJob(JsonNode payload);
}
