package com.filipnikolov.launchpad.deployment.service;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.Deployment;

import java.util.List;

/**
 * Handles the full lifecycle of a deployment — from creating the record
 * to pulling the image and starting the container.
 */
public interface DeploymentService {

    /**
     * Creates a deployment record, pulls the Docker image from DockerHub,
     * injects stored environment variables, and starts the container behind Traefik.
     */
    Deployment createDeployment(CreateDeploymentRequest req);

    /**
     * Returns all deployments.
     */
    List<Deployment> getAllDeployments();

    /**
     * Returns a single deployment by app name.
     */
    Deployment getDeployment(String appName);

    /**
     * Restarts an app by re-pulling its image and recreating the container
     * with the latest stored env vars.
     */
    Deployment restartDeployment(String appName);

    /**
     * Stops a running container and marks the deployment as STOPPED.
     */
    Deployment stopDeployment(String appName);

    /**
     * Marks a deployment as soft-deleted; the cleanup job purges it after the undo window.
     */
    Deployment softDelete(String appName);

    /**
     * Restores a soft-deleted deployment. Throws if the undo window has expired.
     */
    Deployment restore(String appName);

    /**
     * Rolls back to a previously deployed image referenced by an event id.
     * Pins the app to that image and ignores subsequent webhook deploys until unpinned.
     */
    Deployment rollback(String appName, Long eventId);

    /**
     * Releases the pinned image so future webhook deploys take effect again.
     */
    Deployment unpin(String appName);

    /**
     * Idempotently creates a placeholder deployment row for an app before any
     * deploy webhook arrives. Returns the existing row if one already exists.
     */
    Deployment precreate(String appName, int containerPort);
}
