package com.filipnikolov.launchpad.deployment.service;

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
     *
     * @param appName       the application name, used as container name and Traefik subdomain
     * @param repoUrl       the GitHub repository URL for reference
     * @param imageName     the Docker image to pull (e.g. "filipnikolov/myapp:latest")
     * @param containerPort the port the app listens on inside the container
     * @return the persisted Deployment with its final status (RUNNING or FAILED)
     */
    Deployment createDeployment(String appName, String repoUrl, String imageName, int containerPort);

    /**
     * Returns all deployments.
     *
     * @return list of all deployment records
     */
    List<Deployment> getAllDeployments();

    /**
     * Returns a single deployment by app name.
     *
     * @param appName the application name
     * @return the deployment, or throws if not found
     */
    Deployment getDeployment(String appName);

    /**
     * Restarts an app by re-pulling its image and recreating the container
     * with the latest stored env vars.
     *
     * @param appName the application name to restart
     * @return the updated Deployment with its new status
     */
    Deployment restartDeployment(String appName);

    /**
     * Stops a running container and marks the deployment as STOPPED.
     * The uptime monitor will not ping STOPPED apps.
     *
     * @param appName the application name to stop
     * @return the updated Deployment
     */
    Deployment stopDeployment(String appName);
}
