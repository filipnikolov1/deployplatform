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

    /**
     * Last N commits touching a file in the app's repo. Used by the crash-state
     * file-history scrubber.
     */
    @GetMapping("/file-history")
    public ResponseEntity<Map<String, Object>> fileHistory(
            @PathVariable String appName,
            @RequestParam(required = false, defaultValue = "") String path,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        if (path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("available", false, "reason", "path parameter required"));
        }
        return ResponseEntity.ok(service.getFileHistory(appName, path, Math.min(50, Math.max(1, limit))));
    }
}
