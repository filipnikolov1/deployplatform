package com.filipnikolov.launchpad.deployment.controller;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.docker.dto.ContainerStats;
import com.filipnikolov.launchpad.docker.service.ContainerStatsService;
import com.filipnikolov.launchpad.github.dto.CommitsAhead;
import com.filipnikolov.launchpad.github.service.GitHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final ContainerStatsService statsService;
    private final GitHubService gitHubService;

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
    public ResponseEntity<Deployment> restartApp(@PathVariable String appName) {
        return ResponseEntity.ok(deploymentService.restartDeployment(appName));
    }

    /**
     * Stops a running container and marks the deployment as STOPPED.
     */
    @PostMapping("/{appName}/stop")
    public ResponseEntity<Deployment> stopApp(@PathVariable String appName) {
        return ResponseEntity.ok(deploymentService.stopDeployment(appName));
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
