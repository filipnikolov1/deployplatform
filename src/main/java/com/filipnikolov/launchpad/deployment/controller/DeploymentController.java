package com.filipnikolov.launchpad.deployment.controller;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

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
}
