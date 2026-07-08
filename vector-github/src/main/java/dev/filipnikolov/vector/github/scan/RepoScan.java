package dev.filipnikolov.vector.github.scan;

import java.util.List;
import java.util.Optional;

public record RepoScan(List<String> treePaths, boolean truncated, List<ManifestFile> manifests,
                        Optional<String> composeYaml) {}
