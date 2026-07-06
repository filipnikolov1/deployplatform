package dev.filipnikolov.vector.connect.workflow;

import dev.filipnikolov.vector.connect.detect.BuildMode;
import dev.filipnikolov.vector.github.client.dto.StackKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuildpackWorkflowRendererTest {

    private final BuildpackWorkflowRenderer renderer = new BuildpackWorkflowRenderer();

    private ModuleJob buildpackJob(String appName, String modulePath) {
        return new ModuleJob(appName, modulePath, BuildMode.BUILDPACK,
                "ghcr.io/alice/" + appName, StackKind.nextjs, false);
    }

    @Test
    void render_singleBuildpackJob_hasChangesJobAndOneBuildJobNoNotifyStep() {
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(buildpackJob("shop-web", "apps/web")));

        String yaml = renderer.render(spec);

        assertThat(yaml).contains("jobs:");
        assertThat(yaml).contains("changes:");
        assertThat(yaml).contains("build-shop-web:");
        assertThat(yaml).contains("needs: changes");
        assertThat(yaml).doesNotContain("VECTOR_");
        assertThat(yaml).doesNotContain("DOCKERHUB_");
        assertThat(yaml).contains("permissions:");
        assertThat(yaml).contains("contents: read");
        assertThat(yaml).contains("packages: write");
        assertThat(yaml).contains("secrets.GITHUB_TOKEN");
        assertThat(yaml).contains("docker login ghcr.io");
        assertThat(yaml).doesNotContainIgnoringCase("notify");
        assertThat(yaml).doesNotContain("deploy-hook");
    }

    @Test
    void render_monorepo_hasOneChangesJobWithTwoOutputsAndTwoBuildJobsWithDispatchOr() {
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot",
                List.of(buildpackJob("shop-web", "apps/web"), buildpackJob("shop-worker", "apps/worker")));

        String yaml = renderer.render(spec);

        assertThat(countOccurrences(yaml, "changes:")).isEqualTo(1);
        assertThat(yaml).contains("build-shop-web:");
        assertThat(yaml).contains("build-shop-worker:");
        assertThat(countOccurrences(yaml, "needs: changes")).isEqualTo(2);
        assertThat(yaml).contains("if: needs.changes.outputs.shop-web == 'true' || github.event_name == 'workflow_dispatch'");
        assertThat(yaml).contains("if: needs.changes.outputs.shop-worker == 'true' || github.event_name == 'workflow_dispatch'");
    }

    @Test
    void render_dockerfileMode_swapsBuildStepsForDockerBuild() {
        ModuleJob job = new ModuleJob("shop-web", "apps/web", BuildMode.DOCKERFILE,
                "ghcr.io/alice/shop-web", StackKind.custom, false);
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(job));

        String yaml = renderer.render(spec);

        assertThat(yaml).contains("docker build -t ghcr.io/alice/shop-web:${{ github.sha }}");
        assertThat(yaml).contains("-f apps/web/Dockerfile apps/web");
        assertThat(yaml).doesNotContain("pack build");
    }

    @Test
    void render_staticSiteStack_swapsPackStepForWebServersBuildpack() {
        ModuleJob job = new ModuleJob("shop-site", "site", BuildMode.BUILDPACK,
                "ghcr.io/alice/shop-site", StackKind.static_site, false);
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(job));

        String yaml = renderer.render(spec);

        assertThat(yaml).contains("--buildpack paketo-buildpacks/web-servers");
        assertThat(yaml).contains("--env BP_WEB_SERVER=nginx");
        assertThat(yaml).contains("--env BP_WEB_SERVER_ROOT=site");
    }

    @Test
    void render_workspaceBuild_buildsFromRootWithModuleSelectorPerStack() {
        ModuleJob mavenJob = new ModuleJob("shop-api", "vector-api", BuildMode.BUILDPACK,
                "ghcr.io/alice/shop-api", StackKind.springboot, true);
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(mavenJob));

        String yaml = renderer.render(spec);

        assertThat(yaml).contains("pack build ghcr.io/alice/shop-api:${{ github.sha }}");
        assertThat(yaml).doesNotContain("--path vector-api");
        assertThat(yaml).contains("--env BP_MAVEN_BUILT_MODULE=vector-api");
        assertThat(yaml).contains("shared:");
        assertThat(yaml).contains("if: needs.changes.outputs.shop-api == 'true' || needs.changes.outputs.shared == 'true' || github.event_name == 'workflow_dispatch'");
    }

    @Test
    void render_workspaceBuild_nodeStack_usesNodeProjectPathSelector() {
        ModuleJob nodeJob = new ModuleJob("shop-web", "apps/web", BuildMode.BUILDPACK,
                "ghcr.io/alice/shop-web", StackKind.nextjs, true);
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(nodeJob));

        String yaml = renderer.render(spec);

        assertThat(yaml).contains("--env BP_NODE_PROJECT_PATH=apps/web");
    }

    @Test
    void render_workspaceBuild_goStack_usesGoTargetsSelector() {
        ModuleJob goJob = new ModuleJob("shop-updater", "vector-updater", BuildMode.BUILDPACK,
                "ghcr.io/alice/shop-updater", StackKind.go, true);
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(goJob));

        String yaml = renderer.render(spec);

        assertThat(yaml).contains("--env BP_GO_TARGETS=./vector-updater");
    }

    @Test
    void render_workspaceDockerfile_usesRootContext() {
        ModuleJob job = new ModuleJob("shop-api", "vector-api", BuildMode.DOCKERFILE,
                "ghcr.io/alice/shop-api", StackKind.custom, true);
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(job));

        String yaml = renderer.render(spec);

        assertThat(yaml).contains("-f vector-api/Dockerfile .");
    }

    @Test
    void render_markerLineHasVersionAndAppSlug() {
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 3, "vector-bot", List.of(buildpackJob("shop-web", "apps/web")));

        String yaml = renderer.render(spec);

        assertThat(yaml).startsWith("# vector:managed v3 app=vector-bot");
    }

    @Test
    void render_expectedJobsMatchesRenderedJobAndStepNames() {
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot",
                List.of(buildpackJob("shop-web", "apps/web"), buildpackJob("shop-worker", "apps/worker")));

        List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs = renderer.expectedJobs(spec);

        assertThat(expectedJobs).extracting(BuildpackWorkflowRenderer.ExpectedJob::jobName)
                .containsExactly("build-shop-web", "build-shop-worker");
        assertThat(expectedJobs).extracting(BuildpackWorkflowRenderer.ExpectedJob::appName)
                .containsExactly("shop-web", "shop-worker");
        assertThat(expectedJobs.get(0).stepNames()).containsExactly(
                "Checkout", "Set up pack", "Log in to GHCR", "Build (buildpacks)", "Push");
    }

    @Test
    void blocksList_isAppendFriendly_insertingExtraBlockDoesNotBreakExistingJobs() {
        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                "alice/shop", "main", 1, "vector-bot", List.of(buildpackJob("shop-web", "apps/web")));

        List<String> blocks = new java.util.ArrayList<>(renderer.renderBlocks(spec));
        blocks.add("  extra-job:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo hi\n");
        String yaml = String.join("", blocks);

        assertThat(yaml).contains("changes:");
        assertThat(yaml).contains("build-shop-web:");
        assertThat(yaml).contains("extra-job:");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
