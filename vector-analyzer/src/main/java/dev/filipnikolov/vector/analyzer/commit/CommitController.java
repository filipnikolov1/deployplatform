package dev.filipnikolov.vector.analyzer.commit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyzer/apps/{appName}/commits")
public class CommitController {

    private final CommitService service;

    public CommitController(CommitService service) {
        this.service = service;
    }

    @GetMapping("/{sha}")
    public ResponseEntity<Map<String, Object>> commitDetail(
            @PathVariable String appName,
            @PathVariable String sha) {
        return ResponseEntity.ok(service.getCommitDetail(appName, sha));
    }

    @GetMapping("/{sha}/logs")
    public ResponseEntity<List<Map<String, Object>>> commitLogs(
            @PathVariable String appName,
            @PathVariable String sha) {
        return ResponseEntity.ok(service.getCommitLogs(appName, sha));
    }

    @GetMapping("/{sha}/diff")
    public ResponseEntity<Map<String, Object>> commitDiff(
            @PathVariable String appName,
            @PathVariable String sha) {
        return ResponseEntity.ok(service.getCommitDiff(appName, sha));
    }

    @GetMapping("/{sha}/files")
    public ResponseEntity<Map<String, Object>> fileAtCommit(
            @PathVariable String appName,
            @PathVariable String sha,
            @RequestParam(required = false, defaultValue = "") String path) {
        if (path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("available", false, "reason", "path parameter required"));
        }
        return ResponseEntity.ok(service.getFileAtCommit(appName, sha, path));
    }
}
