package dev.filipnikolov.vector.connect.detect;

import dev.filipnikolov.vector.github.client.dto.StackKind;
import dev.filipnikolov.vector.github.scan.ManifestFile;
import dev.filipnikolov.vector.github.scan.RepoScan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleDetector {

    public List<ModuleCandidate> detect(RepoScan scan) {
        Map<String, List<ManifestFile>> manifestsByDir = new LinkedHashMap<>();
        for (ManifestFile manifest : scan.manifests()) {
            manifestsByDir.computeIfAbsent(dirOf(manifest.path()), d -> new ArrayList<>()).add(manifest);
        }

        List<ModuleCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<ManifestFile>> entry : manifestsByDir.entrySet()) {
            ModuleCandidate candidate = detectForDir(entry.getKey(), entry.getValue());
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            ModuleCandidate staticSite = detectStaticSite(scan.treePaths());
            if (staticSite != null) {
                candidates.add(staticSite);
            }
            return candidates;
        }

        boolean hasSubdirCandidate = candidates.stream().anyMatch(c -> !c.path().isEmpty());
        if (hasSubdirCandidate) {
            candidates.removeIf(c -> c.path().isEmpty() && isWorkspaceWrapper(manifestsByDir.get("")));
        }

        return candidates;
    }

    private ModuleCandidate detectForDir(String dir, List<ManifestFile> manifests) {
        ManifestFile dockerfile = findByFilename(manifests, "Dockerfile");

        ManifestFile packageJson = findByFilename(manifests, "package.json");
        if (packageJson != null) {
            boolean isNext = packageJson.content().contains("\"next\"");
            StackKind stack = isNext ? StackKind.nextjs : StackKind.node;
            return candidateFor(dir, stack, 3000, dockerfile, dir.isEmpty() && isWorkspaceWrapperContent(packageJson));
        }

        ManifestFile goMod = findByFilename(manifests, "go.mod");
        if (goMod != null) {
            return candidateFor(dir, StackKind.go, 8080, dockerfile, false);
        }

        ManifestFile pomXml = findByFilename(manifests, "pom.xml");
        ManifestFile buildGradle = findByFilename(manifests, "build.gradle");
        ManifestFile buildGradleKts = findByFilename(manifests, "build.gradle.kts");
        if (pomXml != null || buildGradle != null || buildGradleKts != null) {
            boolean isWorkspaceRoot = dir.isEmpty() && pomXml != null && pomXml.content().contains("<modules>");
            return candidateFor(dir, StackKind.springboot, 8080, dockerfile, isWorkspaceRoot);
        }

        ManifestFile requirementsTxt = findByFilename(manifests, "requirements.txt");
        ManifestFile pyprojectToml = findByFilename(manifests, "pyproject.toml");
        if (requirementsTxt != null || pyprojectToml != null) {
            return candidateFor(dir, StackKind.python, 8000, dockerfile, false);
        }

        if (dockerfile != null) {
            return new ModuleCandidate(dir, StackKind.custom, BuildMode.DOCKERFILE, null, 0.6);
        }

        return null;
    }

    private ModuleCandidate candidateFor(String dir, StackKind stack, int port, ManifestFile dockerfile,
                                          boolean isWorkspaceRoot) {
        BuildMode buildMode = dockerfile != null ? BuildMode.DOCKERFILE : BuildMode.BUILDPACK;
        double confidence = isWorkspaceRoot ? 0.5 : 1.0;
        return new ModuleCandidate(dir, stack, buildMode, port, confidence);
    }

    private boolean isWorkspaceWrapper(List<ManifestFile> rootManifests) {
        if (rootManifests == null) {
            return false;
        }
        ManifestFile packageJson = findByFilename(rootManifests, "package.json");
        if (packageJson != null && isWorkspaceWrapperContent(packageJson)) {
            return true;
        }
        ManifestFile pomXml = findByFilename(rootManifests, "pom.xml");
        return pomXml != null && pomXml.content().contains("<modules>");
    }

    private boolean isWorkspaceWrapperContent(ManifestFile packageJson) {
        return packageJson.content().contains("\"workspaces\"");
    }

    private ManifestFile findByFilename(List<ManifestFile> manifests, String filename) {
        for (ManifestFile manifest : manifests) {
            if (filenameOf(manifest.path()).equals(filename)) {
                return manifest;
            }
        }
        return null;
    }

    private ModuleCandidate detectStaticSite(List<String> treePaths) {
        String rootIndexHtml = treePaths.stream().filter(p -> p.equals("index.html")).findFirst().orElse(null);
        if (rootIndexHtml != null) {
            return new ModuleCandidate("", StackKind.static_site, BuildMode.BUILDPACK, 8080, 0.7);
        }

        String dir = null;
        for (String path : treePaths) {
            if (filenameOf(path).equals("index.html") && path.contains("/") && dirOf(path).indexOf('/') == -1) {
                if (dir != null && !dir.equals(dirOf(path))) {
                    return null;
                }
                dir = dirOf(path);
            }
        }
        if (dir != null) {
            return new ModuleCandidate(dir, StackKind.static_site, BuildMode.BUILDPACK, 8080, 0.7);
        }
        return null;
    }

    private String dirOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx == -1 ? "" : path.substring(0, idx);
    }

    private String filenameOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx == -1 ? path : path.substring(idx + 1);
    }
}
