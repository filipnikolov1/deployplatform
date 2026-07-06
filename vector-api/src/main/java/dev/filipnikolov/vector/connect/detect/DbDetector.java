package dev.filipnikolov.vector.connect.detect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.filipnikolov.vector.github.scan.ManifestFile;
import dev.filipnikolov.vector.github.scan.RepoScan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class DbDetector {

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
    private static final List<String> DB_DRIVER_DEPS = List.of("psycopg2", "postgresql", "pg");
    private static final List<String> COMPOSE_DB_IMAGES = List.of("postgres", "mysql");
    private static final Set<String> IGNORED_SEGMENTS = Set.of(
            "node_modules", ".git", ".github", "vendor", "dist", "build", "target", "__pycache__");

    public DbSuggestion suggest(RepoScan scan) {
        List<String> signals = new ArrayList<>();

        if (hasSegmentPath(scan, "prisma")) {
            signals.add("prisma directory");
        }

        for (ManifestFile manifest : scan.manifests()) {
            if (manifest.content().contains("@prisma/client")) {
                signals.add("@prisma/client dependency in " + manifest.path());
            }
            if (manifest.content().contains("DATABASE_URL") || manifest.content().contains("SPRING_DATASOURCE_URL")) {
                signals.add("database URL in " + manifest.path());
            }
            for (String driver : DB_DRIVER_DEPS) {
                if (Pattern.compile("\\b" + Pattern.quote(driver) + "\\b").matcher(manifest.content()).find()) {
                    signals.add(driver + " driver dependency in " + manifest.path());
                    break;
                }
            }
        }

        if (hasSegmentPath(scan, "migrations")) {
            signals.add("migrations directory");
        }
        if (hasSegmentPair(scan, "db", "migrate")) {
            signals.add("db/migrate directory");
        }

        scan.composeYaml().ifPresent(yaml -> {
            String image = extractComposeImage(yaml);
            if (image != null) {
                for (String dbImage : COMPOSE_DB_IMAGES) {
                    if (image.contains(dbImage)) {
                        signals.add("compose service image " + dbImage);
                        break;
                    }
                }
            }
        });

        Likelihood likelihood;
        if (signals.isEmpty()) {
            likelihood = Likelihood.NONE;
        } else if (signals.size() == 1) {
            likelihood = Likelihood.LIKELY;
        } else {
            likelihood = Likelihood.CERTAIN;
        }

        return new DbSuggestion(likelihood, signals);
    }

    private boolean hasSegmentPath(RepoScan scan, String segment) {
        return scan.treePaths().stream().anyMatch(p -> containsSegment(p, segment));
    }

    private boolean hasSegmentPair(RepoScan scan, String first, String second) {
        return scan.treePaths().stream().anyMatch(p -> containsSegmentPair(p, first, second));
    }

    private boolean containsSegment(String path, String segment) {
        String[] segments = path.split("/");
        for (String s : segments) {
            if (IGNORED_SEGMENTS.contains(s)) {
                return false;
            }
        }
        for (String s : segments) {
            if (s.equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSegmentPair(String path, String first, String second) {
        String[] segments = path.split("/");
        for (String s : segments) {
            if (IGNORED_SEGMENTS.contains(s)) {
                return false;
            }
        }
        for (int i = 0; i < segments.length - 1; i++) {
            if (segments[i].equals(first) && segments[i + 1].equals(second)) {
                return true;
            }
        }
        return false;
    }

    private String extractComposeImage(String yaml) {
        try {
            JsonNode root = YAML_MAPPER.readTree(yaml);
            JsonNode services = root.get("services");
            if (services == null) {
                return null;
            }
            StringBuilder images = new StringBuilder();
            services.properties().forEach(entry -> {
                JsonNode image = entry.getValue().get("image");
                if (image != null) {
                    images.append(image.asText()).append(" ");
                }
            });
            return images.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
