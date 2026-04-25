package dev.filipnikolov.vector.ai.controller;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.AiRequest;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.docker.service.DockerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final int MAX_PROMPT_CHARS = 4000;
    private static final int MAX_LOG_BYTES = 8 * 1024;

    private final AiProvider aiProvider;
    private final DockerService dockerService;
    private final DeploymentService deploymentService;
    private final int logTailLines;

    public AiController(
            AiProvider aiProvider,
            DockerService dockerService,
            DeploymentService deploymentService,
            @Value("${vector.ai.log-tail-lines}") int logTailLines) {
        this.aiProvider = aiProvider;
        this.dockerService = dockerService;
        this.deploymentService = deploymentService;
        this.logTailLines = logTailLines;
    }

    @PostMapping(value = "/ask",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ask(@RequestBody(required = false) String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Prompt must not be blank");
        }
        if (prompt.length() > MAX_PROMPT_CHARS) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Prompt exceeds maximum length of " + MAX_PROMPT_CHARS + " chars");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(aiProvider.analyze(new AiRequest(prompt)).text());
    }

    @GetMapping(value = "/logs/analyze", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> analyzeLogs(@RequestParam("app") String appName) {
        if (appName == null || appName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "app query parameter must not be blank");
        }

        deploymentService.getDeployment(appName);

        List<String> logLines = dockerService.getContainerLogs(appName, logTailLines);
        if (logLines == null || logLines.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No logs available for this app yet.");
        }

        String truncated = truncateToTailBytes(String.join("\n", logLines), MAX_LOG_BYTES);
        String prompt = "You are a DevOps assistant. Analyze this deployment log "
                + "and explain what went wrong in 2-3 sentences: " + truncated;
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(aiProvider.analyze(new AiRequest(prompt)).text());
    }

    private static String truncateToTailBytes(String content, int maxBytes) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return content;
        }
        byte[] tail = new byte[maxBytes];
        System.arraycopy(bytes, bytes.length - maxBytes, tail, 0, maxBytes);
        return new String(tail, StandardCharsets.UTF_8);
    }
}
