package dev.filipnikolov.vector.deployment.service;

import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;

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
     * Sets a custom Traefik subdomain for the app. Pass null to revert to the default (appName).
     * Triggers a restart if the app is currently running so the new label takes effect.
     */
    Deployment updateSubdomain(String appName, String subdomain);

    /**
     * Handles a webhook deploy request asynchronously. Checks for pinned image before deploying.
     * The caller must supply the already-acquired lock handle; this method releases it in a
     * finally block when the deploy (or pinned-image recording) completes.
     */
    void handleWebhookDeployAsync(CreateDeploymentRequest req, ActionLockService.LockHandle lock);
}
