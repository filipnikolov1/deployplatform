package dev.filipnikolov.vector.connect.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import dev.filipnikolov.vector.connect.workflow.BuildpackWorkflowRenderer;
import dev.filipnikolov.vector.progress.ProgressFrame;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Merges a {@code workflow_job} webhook payload's {@code steps[]} against the {@code
 * expected_jobs} snapshot taken at workflow render time, so the frontend's per-step skeleton
 * ticks using GitHub's own step names/timestamps. Expected steps not yet present in the
 * payload are emitted as a pending skeleton (no timestamps) so the frontend can render the
 * full step list before GitHub reports any progress.
 */
public class BuildProgressMapper {

    public List<ProgressFrame> mapSteps(JsonNode payload, List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs) {
        List<ProgressFrame> frames = new ArrayList<>();
        Optional<BuildpackWorkflowRenderer.ExpectedJob> matchedJob = expectedJob(payload, expectedJobs);
        if (matchedJob.isEmpty()) {
            return frames;
        }

        JsonNode job = payload.path("workflow_job");
        JsonNode steps = job.path("steps");
        Map<String, JsonNode> payloadSteps = new LinkedHashMap<>();
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                String name = step.path("name").asText(null);
                if (name != null) {
                    payloadSteps.put(name, step);
                }
            }
        }

        for (String stepName : matchedJob.get().stepNames()) {
            JsonNode step = payloadSteps.get(stepName);
            Instant startedAt = step != null ? parseInstant(step.path("started_at")) : null;
            Instant completedAt = step != null ? parseInstant(step.path("completed_at")) : null;
            frames.add(new ProgressFrame("BUILD_STEP", stepName, null, null, null, Instant.now(),
                    startedAt, completedAt));
        }
        return frames;
    }

    private Instant parseInstant(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<BuildpackWorkflowRenderer.ExpectedJob> expectedJob(
            JsonNode payload, List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs) {
        String jobName = payload.path("workflow_job").path("name").asText(null);
        if (jobName == null) {
            return Optional.empty();
        }
        return expectedJobs.stream().filter(job -> job.jobName().equals(jobName)).findFirst();
    }

    public Optional<String> appNameForJob(JsonNode payload, List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs) {
        return expectedJob(payload, expectedJobs).map(BuildpackWorkflowRenderer.ExpectedJob::appName);
    }
}
