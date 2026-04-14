package com.filipnikolov.launchpad.deployment.controller;

import com.filipnikolov.launchpad.common.lock.ActionLockService;
import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.docker.dto.ContainerStats;
import com.filipnikolov.launchpad.docker.service.ContainerStatsService;
import com.filipnikolov.launchpad.github.dto.CommitsAhead;
import com.filipnikolov.launchpad.github.service.GitHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final ContainerStatsService statsService;
    private final GitHubService gitHubService;
    private final ActionLockService lockService;

    /**
     * Lists all deployments with their current status.
     */
    @GetMapping
    public ResponseEntity<List<Deployment>> getAllApps() {
        return ResponseEntity.ok(deploymentService.getAllDeployments());
    }

    /**
     * Returns a single deployment by app name.
     */
    @GetMapping("/{appName}")
    public ResponseEntity<Deployment> getApp(@PathVariable String appName) {
        return ResponseEntity.ok(deploymentService.getDeployment(appName));
    }

    /**
     * Restarts an app — re-pulls the image and recreates the container
     * with the latest env vars.
     */
    @PostMapping("/{appName}/restart")
    public ResponseEntity<?> restartApp(@PathVariable String appName) {
        var maybeHandle = lockService.tryLock("app:" + appName);
        if (maybeHandle.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "locked",
                    "lockedUntil", LocalDateTime.now().plusSeconds(5)));
        }
        try (var handle = maybeHandle.get()) {
            return ResponseEntity.ok(deploymentService.restartDeployment(appName));
        }
    }

    /**
     * Stops a running container and marks the deployment as STOPPED.
     */
    @PostMapping("/{appName}/stop")
    public ResponseEntity<?> stopApp(@PathVariable String appName) {
        var maybeHandle = lockService.tryLock("app:" + appName);
        if (maybeHandle.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "locked",
                    "lockedUntil", LocalDateTime.now().plusSeconds(5)));
        }
        try (var handle = maybeHandle.get()) {
            return ResponseEntity.ok(deploymentService.stopDeployment(appName));
        }
    }

    /**
     * Soft-deletes the app — schedules a hard delete after a 5 minute undo window.
     */
    @DeleteMapping("/{appName}")
    public ResponseEntity<Map<String, Object>> softDelete(@PathVariable String appName) {
        Deployment d = deploymentService.softDelete(appName);
        return ResponseEntity.ok(Map.of(
                "undoToken", d.getId(),
                "expiresAt", d.getDeletedAt().plusMinutes(5)));
    }

    /**
     * Restores a soft-deleted app within the undo window.
     */
    @PostMapping("/{appName}/restore")
    public ResponseEntity<Deployment> restore(@PathVariable String appName) {
        return ResponseEntity.ok(deploymentService.restore(appName));
    }

    /**
     * Rolls back to a previously deployed image identified by an event id and
     * pins the app so future webhooks are ignored until unpinned.
     */
    @PostMapping("/{appName}/rollback")
    public ResponseEntity<?> rollback(@PathVariable String appName,
                                      @RequestBody Map<String, Long> body) {
        Long eventId = body.get("eventId");
        if (eventId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventId is required"));
        }
        var maybeHandle = lockService.tryLock("app:" + appName);
        if (maybeHandle.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("error", "locked"));
        }
        try (var handle = maybeHandle.get()) {
            return ResponseEntity.ok(deploymentService.rollback(appName, eventId));
        }
    }

    /**
     * Releases a pinned image so future webhook deploys take effect again.
     */
    @PostMapping("/{appName}/unpin")
    public ResponseEntity<Deployment> unpin(@PathVariable String appName) {
        return ResponseEntity.ok(deploymentService.unpin(appName));
    }

    /**
     * Returns live container stats (cpu, memory, uptime, restart count).
     */
    @GetMapping("/{appName}/stats")
    public ResponseEntity<ContainerStats> stats(@PathVariable String appName) {
        return ResponseEntity.ok(statsService.get(appName));
    }

    /**
     * Returns the commits in (deployed..branch) for the app — fuels the
     * "commits ahead" pill on the dashboard.
     */
    @GetMapping("/{appName}/commits-ahead")
    public ResponseEntity<CommitsAhead> commitsAhead(@PathVariable String appName) {
        Deployment d = deploymentService.getDeployment(appName);
        if (d.getCommitSha() == null || d.getBranch() == null || d.getRepoUrl() == null) {
            return ResponseEntity.ok(new CommitsAhead(0, java.util.List.of(), null));
        }
        return ResponseEntity.ok(gitHubService.compare(d.getRepoUrl(), d.getCommitSha(), d.getBranch()));
    }
}
