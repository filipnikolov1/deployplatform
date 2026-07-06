package dev.filipnikolov.vector.connect.controller;

import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.connect.dto.ConnectResponse;
import dev.filipnikolov.vector.connect.dto.ScanResponse;
import dev.filipnikolov.vector.connect.service.AppNameTakenException;
import dev.filipnikolov.vector.connect.service.AppNotFoundException;
import dev.filipnikolov.vector.connect.service.ConnectService;
import dev.filipnikolov.vector.connect.service.CustomWorkflowRedeployException;
import dev.filipnikolov.vector.connect.service.InstallationNotApprovedException;
import dev.filipnikolov.vector.connect.workflow.WiringResult;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/connect")
public class ConnectController {

    private final ConnectService connectService;

    public ConnectController(ConnectService connectService) {
        this.connectService = connectService;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody Map<String, String> body) {
        String repoFullName = body.get("repoFullName");
        ScanResponse response = connectService.scan(repoFullName);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> connect(@RequestBody ConnectRequest req) {
        try {
            ConnectResponse response = connectService.connect(req);
            return ResponseEntity.ok(response);
        } catch (InstallationNotApprovedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (AppNameTakenException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{appName}/ci-status")
    public ResponseEntity<?> ciStatus(@PathVariable String appName) {
        try {
            return ResponseEntity.ok(connectService.ciStatus(appName));
        } catch (AppNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{appName}/workflow-mode")
    public ResponseEntity<?> workflowMode(@PathVariable String appName, @RequestBody Map<String, String> body) {
        try {
            WorkflowMode mode = WorkflowMode.valueOf(body.get("mode"));
            WiringResult result = connectService.switchWorkflowMode(appName, mode);
            return ResponseEntity.ok(result);
        } catch (AppNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{appName}/disconnect")
    public ResponseEntity<?> disconnect(@PathVariable String appName) {
        try {
            connectService.disconnect(appName);
            return ResponseEntity.ok(Map.of("status", "disconnected"));
        } catch (AppNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{appName}/redeploy")
    public ResponseEntity<?> redeploy(@PathVariable String appName) {
        try {
            connectService.redeploy(appName);
            return ResponseEntity.ok(Map.of("status", "dispatched"));
        } catch (AppNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (CustomWorkflowRedeployException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
