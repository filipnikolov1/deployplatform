package com.filipnikolov.launchpad.envvar.service;

import java.util.Map;

/**
 * Manages per-app environment variables. Values are stored encrypted in the database
 * and decrypted when retrieved for container injection.
 */
public interface EnvVarService {

    /**
     * Sets an environment variable for an app. Creates a new entry or updates
     * an existing one if the key already exists.
     *
     * @param appName the application name
     * @param key     the environment variable name
     * @param value   the plaintext value (will be encrypted before storage)
     */
    void setEnvVar(String appName, String key, String value);

    /**
     * Retrieves all environment variables for an app, decrypted.
     *
     * @param appName the application name
     * @return a map of variable names to their plaintext values
     */
    Map<String, String> getEnvVars(String appName);

    /**
     * Deletes an environment variable for an app.
     *
     * @param appName the application name
     * @param key     the environment variable name to delete
     */
    void deleteEnvVar(String appName, String key);
}
