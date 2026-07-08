package dev.filipnikolov.vector.connect.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.connect.workflow.BuildpackWorkflowRenderer;
import dev.filipnikolov.vector.progress.ProgressFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuildProgressMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BuildProgressMapper mapper = new BuildProgressMapper();

    @Test
    void mapSteps_mergesPayloadStepsAgainstExpectedJobsForMatchingJob() throws Exception {
        List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs = List.of(
                new BuildpackWorkflowRenderer.ExpectedJob("build-shop", "shop",
                        List.of("Checkout", "Set up pack", "Log in to GHCR", "Build (buildpacks)")));

        JsonNode payload = MAPPER.readTree("""
                {
                  "action": "in_progress",
                  "workflow_job": {
                    "name": "build-shop",
                    "status": "in_progress",
                    "steps": [
                      { "name": "Checkout", "status": "completed", "conclusion": "success", "number": 1,
                        "started_at": "2026-07-08T10:00:00Z", "completed_at": "2026-07-08T10:00:05Z" },
                      { "name": "Set up pack", "status": "in_progress", "conclusion": null, "number": 2,
                        "started_at": "2026-07-08T10:00:05Z", "completed_at": null }
                    ]
                  }
                }
                """);

        List<ProgressFrame> frames = mapper.mapSteps(payload, expectedJobs);

        assertThat(frames).extracting(ProgressFrame::stage).containsExactly(
                "BUILD_STEP", "BUILD_STEP", "BUILD_STEP", "BUILD_STEP");
        assertThat(frames).extracting(ProgressFrame::message).containsExactly(
                "Checkout", "Set up pack", "Log in to GHCR", "Build (buildpacks)");
        assertThat(frames.get(0).startedAt()).isEqualTo(Instant.parse("2026-07-08T10:00:00Z"));
        assertThat(frames.get(0).completedAt()).isEqualTo(Instant.parse("2026-07-08T10:00:05Z"));
        assertThat(frames.get(1).startedAt()).isEqualTo(Instant.parse("2026-07-08T10:00:05Z"));
        assertThat(frames.get(1).completedAt()).isNull();
        assertThat(frames.get(2).startedAt()).isNull();
        assertThat(frames.get(2).completedAt()).isNull();
        assertThat(frames.get(3).startedAt()).isNull();
        assertThat(frames.get(3).completedAt()).isNull();
    }

    @Test
    void mapSteps_jobNameNotInExpectedJobs_returnsEmpty() throws Exception {
        List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs = List.of(
                new BuildpackWorkflowRenderer.ExpectedJob("build-shop", "shop", List.of("Checkout")));

        JsonNode payload = MAPPER.readTree("""
                {
                  "workflow_job": { "name": "build-other", "status": "in_progress", "steps": [] }
                }
                """);

        List<ProgressFrame> frames = mapper.mapSteps(payload, expectedJobs);

        assertThat(frames).isEmpty();
    }

    @Test
    void mapSteps_noStepsInPayload_returnsPendingSkeletonForExpectedSteps() throws Exception {
        List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs = List.of(
                new BuildpackWorkflowRenderer.ExpectedJob("build-shop", "shop", List.of("Checkout")));

        JsonNode payload = MAPPER.readTree("""
                { "workflow_job": { "name": "build-shop", "status": "queued" } }
                """);

        List<ProgressFrame> frames = mapper.mapSteps(payload, expectedJobs);

        assertThat(frames).extracting(ProgressFrame::message).containsExactly("Checkout");
        assertThat(frames.get(0).startedAt()).isNull();
        assertThat(frames.get(0).completedAt()).isNull();
    }

    @Test
    void appNameForJob_matchesConventionAndReturnsMappedAppName() throws Exception {
        List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs = List.of(
                new BuildpackWorkflowRenderer.ExpectedJob("build-shop", "shop", List.of("Checkout")));

        JsonNode payload = MAPPER.readTree("""
                { "workflow_job": { "name": "build-shop" } }
                """);

        var appName = mapper.appNameForJob(payload, expectedJobs);

        assertThat(appName).contains("shop");
    }

    @Test
    void appNameForJob_unknownJobName_returnsEmpty() throws Exception {
        JsonNode payload = MAPPER.readTree("""
                { "workflow_job": { "name": "build-unknown" } }
                """);

        var appName = mapper.appNameForJob(payload, List.of());

        assertThat(appName).isEmpty();
    }
}
