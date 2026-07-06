package dev.filipnikolov.vector.connect.detect;

import dev.filipnikolov.vector.github.client.dto.StackKind;
import dev.filipnikolov.vector.github.scan.ManifestFile;
import dev.filipnikolov.vector.github.scan.RepoScan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDetectorTest {

    private final ModuleDetector detector = new ModuleDetector();

    private RepoScan scanOf(List<String> treePaths, List<ManifestFile> manifests) {
        return new RepoScan(treePaths, false, manifests, Optional.empty());
    }

    @Test
    void rule1PackageJsonWithNextDependencyDetectsNextjs() {
        RepoScan scan = scanOf(
                List.of("package.json"),
                List.of(new ManifestFile("package.json", "{\"dependencies\":{\"next\":\"14.0.0\"}}")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.path()).isEqualTo("");
        assertThat(c.stack()).isEqualTo(StackKind.nextjs);
        assertThat(c.portGuess()).isEqualTo(3000);
        assertThat(c.buildMode()).isEqualTo(BuildMode.BUILDPACK);
        assertThat(c.confidence()).isEqualTo(1.0);
    }

    @Test
    void rule1PackageJsonWithoutNextDependencyDetectsNode() {
        RepoScan scan = scanOf(
                List.of("package.json"),
                List.of(new ManifestFile("package.json", "{\"dependencies\":{\"express\":\"4.0.0\"}}")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.stack()).isEqualTo(StackKind.node);
        assertThat(c.portGuess()).isEqualTo(3000);
    }

    @Test
    void rule2GoModDetectsGo() {
        RepoScan scan = scanOf(
                List.of("go.mod"),
                List.of(new ManifestFile("go.mod", "module example.com/api")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.stack()).isEqualTo(StackKind.go);
        assertThat(c.portGuess()).isEqualTo(8080);
    }

    @Test
    void rule3PomXmlDetectsSpringboot() {
        RepoScan scan = scanOf(
                List.of("pom.xml"),
                List.of(new ManifestFile("pom.xml", "<project></project>")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.stack()).isEqualTo(StackKind.springboot);
        assertThat(c.portGuess()).isEqualTo(8080);
    }

    @Test
    void rule3BuildGradleDetectsSpringboot() {
        RepoScan scan = scanOf(
                List.of("build.gradle"),
                List.of(new ManifestFile("build.gradle", "plugins {}")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).stack()).isEqualTo(StackKind.springboot);
    }

    @Test
    void rule4RequirementsTxtDetectsPython() {
        RepoScan scan = scanOf(
                List.of("requirements.txt"),
                List.of(new ManifestFile("requirements.txt", "flask")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.stack()).isEqualTo(StackKind.python);
        assertThat(c.portGuess()).isEqualTo(8000);
    }

    @Test
    void rule4PyprojectTomlDetectsPython() {
        RepoScan scan = scanOf(
                List.of("pyproject.toml"),
                List.of(new ManifestFile("pyproject.toml", "[tool.poetry]")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).stack()).isEqualTo(StackKind.python);
    }

    @Test
    void rule5ManifestPlusDockerfileSameDirSetsDockerfileBuildMode() {
        RepoScan scan = scanOf(
                List.of("go.mod", "Dockerfile"),
                List.of(new ManifestFile("go.mod", "module example.com/api"),
                        new ManifestFile("Dockerfile", "FROM golang")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.stack()).isEqualTo(StackKind.go);
        assertThat(c.buildMode()).isEqualTo(BuildMode.DOCKERFILE);
    }

    @Test
    void rule6DockerfileAloneDetectsCustom() {
        RepoScan scan = scanOf(
                List.of("Dockerfile"),
                List.of(new ManifestFile("Dockerfile", "FROM alpine")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.stack()).isEqualTo(StackKind.custom);
        assertThat(c.buildMode()).isEqualTo(BuildMode.DOCKERFILE);
        assertThat(c.confidence()).isEqualTo(0.6);
    }

    @Test
    void rule7IndexHtmlAtRootWithNoManifestDetectsStaticSite() {
        RepoScan scan = scanOf(List.of("index.html", "style.css"), List.of());

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.stack()).isEqualTo(StackKind.static_site);
        assertThat(c.portGuess()).isEqualTo(8080);
        assertThat(c.confidence()).isEqualTo(0.7);
    }

    @Test
    void rule7IndexHtmlInSingleTopLevelDirWithNoManifestDetectsStaticSite() {
        RepoScan scan = scanOf(List.of("site/index.html", "site/style.css"), List.of());

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        ModuleCandidate c = candidates.get(0);
        assertThat(c.path()).isEqualTo("site");
        assertThat(c.stack()).isEqualTo(StackKind.static_site);
    }

    @Test
    void monorepoNestedManifestDirsAreSeparateCandidates() {
        RepoScan scan = scanOf(
                List.of("apps/web/package.json", "apps/api/go.mod"),
                List.of(new ManifestFile("apps/web/package.json", "{\"dependencies\":{}}"),
                        new ManifestFile("apps/api/go.mod", "module example.com/api")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting(ModuleCandidate::path)
                .containsExactlyInAnyOrder("apps/web", "apps/api");
    }

    @Test
    void workspaceRootPackageJsonExcludedWhenSubdirCandidatesExist() {
        RepoScan scan = scanOf(
                List.of("package.json", "apps/web/package.json"),
                List.of(new ManifestFile("package.json", "{\"workspaces\":[\"apps/*\"]}"),
                        new ManifestFile("apps/web/package.json", "{\"dependencies\":{}}")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).path()).isEqualTo("apps/web");
    }

    @Test
    void workspaceRootPomXmlWithModulesExcludedWhenSubdirCandidatesExist() {
        RepoScan scan = scanOf(
                List.of("pom.xml", "apps/api/pom.xml"),
                List.of(new ManifestFile("pom.xml", "<project><modules><module>apps/api</module></modules></project>"),
                        new ManifestFile("apps/api/pom.xml", "<project></project>")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).path()).isEqualTo("apps/api");
    }

    @Test
    void rootWithWorkspacesKeptWhenNoSubdirCandidates() {
        RepoScan scan = scanOf(
                List.of("package.json"),
                List.of(new ManifestFile("package.json", "{\"workspaces\":[\"apps/*\"]}")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).path()).isEqualTo("");
        assertThat(candidates.get(0).confidence()).isEqualTo(0.5);
    }

    @Test
    void workerDirectoryDefaultsToNotExposed() {
        RepoScan scan = scanOf(
                List.of("apps/worker/package.json"),
                List.of(new ManifestFile("apps/worker/package.json", "{\"dependencies\":{}}")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).exposed()).isFalse();
    }

    @Test
    void nonWorkerDirectoryDefaultsToExposed() {
        RepoScan scan = scanOf(
                List.of("apps/web/package.json"),
                List.of(new ManifestFile("apps/web/package.json", "{\"dependencies\":{}}")));

        List<ModuleCandidate> candidates = detector.detect(scan);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).exposed()).isTrue();
    }
}
