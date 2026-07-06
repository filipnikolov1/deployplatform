package dev.filipnikolov.vector.connect.workflow;

import dev.filipnikolov.vector.github.client.dto.StackKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the managed `vector-deploy.yml` workflow from composable job blocks — a "changes"
 * job (path filters, one output per module) followed by one build job per {@link ModuleJob}.
 * Blocks are concatenated as a list (addendum item 11) so later increments (e.g. DOMPREV) can
 * insert additional jobs without touching the renderer.
 */
public class BuildpackWorkflowRenderer {

    public record RenderSpec(String repoFullName, String branch, int templateVersion, String appSlug,
                              List<ModuleJob> jobs) {}

    public record ExpectedJob(String jobName, String appName, List<String> stepNames) {}

    private static final List<String> BUILDPACK_STEP_NAMES =
            List.of("Checkout", "Set up pack", "Log in to GHCR", "Build (buildpacks)", "Push");
    private static final List<String> DOCKERFILE_STEP_NAMES =
            List.of("Checkout", "Log in to GHCR", "Build (docker)", "Push");

    public String render(RenderSpec spec) {
        return String.join("", renderBlocks(spec));
    }

    public List<ExpectedJob> expectedJobs(RenderSpec spec) {
        List<ExpectedJob> jobs = new ArrayList<>();
        for (ModuleJob job : spec.jobs()) {
            List<String> stepNames = job.mode() == dev.filipnikolov.vector.connect.detect.BuildMode.DOCKERFILE
                    ? DOCKERFILE_STEP_NAMES
                    : BUILDPACK_STEP_NAMES;
            jobs.add(new ExpectedJob("build-" + job.appName(), job.appName(), stepNames));
        }
        return jobs;
    }

    public List<String> renderBlocks(RenderSpec spec) {
        List<String> blocks = new ArrayList<>();
        blocks.add(headerBlock(spec));
        blocks.add(changesJobBlock(spec));
        for (ModuleJob job : spec.jobs()) {
            blocks.add(buildJobBlock(job, hasWorkspaceBuild(spec)));
        }
        return blocks;
    }

    private boolean hasWorkspaceBuild(RenderSpec spec) {
        return spec.jobs().stream().anyMatch(ModuleJob::workspaceBuild);
    }

    private String headerBlock(RenderSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("# vector:managed v").append(spec.templateVersion()).append(" app=").append(spec.appSlug())
                .append(" — managed by Deploy Platform; do not edit by hand\n");
        sb.append("name: deploy\n");
        sb.append("on:\n");
        sb.append("  push:\n");
        sb.append("    branches: [\"").append(spec.branch()).append("\"]\n");
        sb.append("  workflow_dispatch: {}\n");
        sb.append("permissions:          # (2026-07-06 GHCR) GITHUB_TOKEN pushes the packages — no secrets anywhere\n");
        sb.append("  contents: read\n");
        sb.append("  packages: write\n");
        sb.append("jobs:\n");
        return sb.toString();
    }

    private String changesJobBlock(RenderSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("  changes:\n");
        sb.append("    runs-on: ubuntu-latest\n");
        sb.append("    outputs:\n");
        for (ModuleJob job : spec.jobs()) {
            sb.append("      ").append(job.appName()).append(": ${{ steps.filter.outputs.")
                    .append(job.appName()).append(" }}\n");
        }
        if (hasWorkspaceBuild(spec)) {
            sb.append("      shared: ${{ steps.filter.outputs.shared }}\n");
        }
        sb.append("    steps:\n");
        sb.append("      - uses: actions/checkout@v4\n");
        sb.append("      - uses: dorny/paths-filter@v3\n");
        sb.append("        id: filter\n");
        sb.append("        with:\n");
        sb.append("          filters: |\n");
        for (ModuleJob job : spec.jobs()) {
            sb.append("            ").append(job.appName()).append(":\n");
            sb.append("              - '").append(job.modulePath()).append("/**'\n");
            sb.append("              - '.github/workflows/vector-deploy.yml'\n");
        }
        if (hasWorkspaceBuild(spec)) {
            sb.append("            shared:\n");
            for (ModuleJob job : spec.jobs()) {
                sb.append("              - '!").append(job.modulePath()).append("/**'\n");
            }
        }
        return sb.toString();
    }

    private String buildJobBlock(ModuleJob job, boolean workspaceRepo) {
        StringBuilder sb = new StringBuilder();
        sb.append("  build-").append(job.appName()).append(":\n");
        sb.append("    needs: changes\n");
        sb.append("    # Job-level gating (C14): a skipped module reports conclusion=skipped in the\n");
        sb.append("    # workflow_job event, so Vector knows exactly which apps built. The\n");
        sb.append("    # workflow_dispatch OR is the first-run escape — paths-filter has no diff\n");
        sb.append("    # base on manual dispatch.\n");
        sb.append("    if: needs.changes.outputs.").append(job.appName()).append(" == 'true'");
        if (workspaceRepo) {
            sb.append(" || needs.changes.outputs.shared == 'true'");
        }
        sb.append(" || github.event_name == 'workflow_dispatch'\n");
        sb.append("    runs-on: ubuntu-latest\n");
        sb.append("    steps:\n");
        sb.append("      - uses: actions/checkout@v4\n");
        if (job.mode() == dev.filipnikolov.vector.connect.detect.BuildMode.DOCKERFILE) {
            appendDockerfileSteps(sb, job);
        } else {
            appendBuildpackSteps(sb, job);
        }
        return sb.toString();
    }

    private void appendDockerfileSteps(StringBuilder sb, ModuleJob job) {
        sb.append("      - name: Log in to GHCR\n");
        sb.append("        run: echo \"${{ secrets.GITHUB_TOKEN }}\" | docker login ghcr.io -u \"${{ github.actor }}\" --password-stdin\n");
        sb.append("      - name: Build (docker)\n");
        String context = job.workspaceBuild() ? "." : job.modulePath();
        sb.append("        run: docker build -t ").append(job.imageTarget()).append(":${{ github.sha }} -t ")
                .append(job.imageTarget()).append(":latest -f ").append(job.modulePath())
                .append("/Dockerfile ").append(context).append("\n");
        sb.append("      - name: Push\n");
        sb.append("        run: docker push --all-tags ").append(job.imageTarget()).append("\n");
    }

    private void appendBuildpackSteps(StringBuilder sb, ModuleJob job) {
        sb.append("      - name: Set up pack\n");
        sb.append("        uses: buildpacks/github-actions/setup-pack@v5.8.11\n");
        sb.append("      - name: Log in to GHCR\n");
        sb.append("        run: echo \"${{ secrets.GITHUB_TOKEN }}\" | docker login ghcr.io -u \"${{ github.actor }}\" --password-stdin\n");
        sb.append("      - name: Build (buildpacks)\n");
        sb.append("        run: |\n");
        sb.append("          pack build ").append(job.imageTarget()).append(":${{ github.sha }} \\\n");
        if (!job.workspaceBuild()) {
            sb.append("            --path ").append(job.modulePath()).append(" \\\n");
        }
        sb.append("            --builder paketobuildpacks/builder-jammy-base \\\n");
        if (job.stack() == StackKind.static_site) {
            sb.append("            --buildpack paketo-buildpacks/web-servers \\\n");
            sb.append("            --env BP_WEB_SERVER=nginx \\\n");
            sb.append("            --env BP_WEB_SERVER_ROOT=").append(job.modulePath()).append(" \\\n");
        } else if (job.workspaceBuild()) {
            sb.append("            --env ").append(moduleSelector(job)).append(" \\\n");
        }
        sb.append("            --tag ").append(job.imageTarget()).append(":latest\n");
        sb.append("      - name: Push\n");
        sb.append("        run: docker push --all-tags ").append(job.imageTarget()).append("\n");
    }

    private String moduleSelector(ModuleJob job) {
        return switch (job.stack()) {
            case springboot -> "BP_MAVEN_BUILT_MODULE=" + job.modulePath();
            case nextjs, node -> "BP_NODE_PROJECT_PATH=" + job.modulePath();
            case go -> "BP_GO_TARGETS=./" + job.modulePath();
            case python, custom, static_site -> "BP_MAVEN_BUILT_MODULE=" + job.modulePath();
        };
    }
}
