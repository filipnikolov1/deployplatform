package com.filipnikolov.launchpad.deployment.service;

import com.filipnikolov.launchpad.deployment.model.Deployment;

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
}
