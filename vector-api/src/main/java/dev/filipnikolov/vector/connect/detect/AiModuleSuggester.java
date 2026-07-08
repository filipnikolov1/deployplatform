package dev.filipnikolov.vector.connect.detect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.AiProviderException;
import dev.filipnikolov.vector.ai.AiRequest;
import dev.filipnikolov.vector.ai.AiResponse;
import dev.filipnikolov.vector.github.client.dto.StackKind;
import dev.filipnikolov.vector.github.scan.RepoScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiModuleSuggester {

    private static final Logger log = LoggerFactory.getLogger(AiModuleSuggester.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TREE_LINES = 400;

    private final AiProvider aiProvider;

    public AiModuleSuggester(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public List<ModuleCandidate> refine(RepoScan scan, List<ModuleCandidate> deterministic) {
        if (!(deterministic.isEmpty() || deterministic.size() > 1)) {
            return deterministic;
        }

        String prompt = buildPrompt(scan, deterministic);
        AiResponse response;
        try {
            response = aiProvider.analyze(new AiRequest(prompt));
        } catch (AiProviderException e) {
            log.warn("AI module suggestion failed, using deterministic candidates: {}", e.getMessage());
            return deterministic;
        }

        return merge(scan, deterministic, response.text());
    }

    private String buildPrompt(RepoScan scan, List<ModuleCandidate> deterministic) {
        StringBuilder sb = new StringBuilder();
        sb.append("File tree:\n");
        scan.treePaths().stream().limit(MAX_TREE_LINES).forEach(p -> sb.append(p).append('\n'));
        sb.append("Manifest files:\n");
        scan.manifests().forEach(m -> sb.append(m.path()).append('\n'));
        sb.append("Deterministic candidates:\n");
        deterministic.forEach(c -> sb.append(c.path()).append(' ').append(c.stack())
                .append(' ').append(c.portGuess()).append('\n'));
        sb.append("Respond with JSON: [{path, stack, port, include}]");
        return sb.toString();
    }

    private List<ModuleCandidate> merge(RepoScan scan, List<ModuleCandidate> deterministic, String aiText) {
        JsonNode root;
        try {
            root = MAPPER.readTree(aiText);
            if (!root.isArray()) {
                throw new IllegalArgumentException("AI response is not a JSON array");
            }
        } catch (Exception e) {
            log.warn("Malformed AI module suggestion response, using deterministic candidates: {}", e.getMessage());
            return deterministic;
        }

        Set<String> treePaths = Set.copyOf(scan.treePaths());
        Set<String> treeDirs = new HashSet<>();
        for (String path : scan.treePaths()) {
            int idx = path.lastIndexOf('/');
            if (idx != -1) {
                treeDirs.add(path.substring(0, idx));
            }
        }

        Map<String, ModuleCandidate> result = new LinkedHashMap<>();
        for (ModuleCandidate c : deterministic) {
            result.put(c.path(), c);
        }

        try {
            for (JsonNode entry : root) {
                String path = entry.get("path").asText();
                boolean include = !entry.has("include") || entry.get("include").asBoolean();

                ModuleCandidate existing = result.get(path);
                if (existing != null) {
                    if (!include) {
                        result.remove(path);
                        continue;
                    }
                    Integer port = entry.has("port") ? entry.get("port").asInt() : existing.portGuess();
                    result.put(path, new ModuleCandidate(existing.path(), existing.stack(), existing.buildMode(),
                            port, existing.confidence(), existing.exposed()));
                } else {
                    if (!include) {
                        continue;
                    }
                    if (!treePaths.contains(path) && !treeDirs.contains(path) && !path.isEmpty()) {
                        continue;
                    }
                    StackKind stack = StackKind.valueOf(entry.get("stack").asText());
                    Integer port = entry.has("port") ? entry.get("port").asInt() : null;
                    result.put(path, new ModuleCandidate(path, stack, BuildMode.BUILDPACK, port, 0.5));
                }
            }
        } catch (Exception e) {
            log.warn("Malformed AI module suggestion entry, using deterministic candidates: {}", e.getMessage());
            return deterministic;
        }

        return new ArrayList<>(result.values());
    }
}
