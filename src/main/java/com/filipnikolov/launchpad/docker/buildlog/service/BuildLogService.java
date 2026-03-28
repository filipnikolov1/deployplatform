package com.filipnikolov.launchpad.docker.buildlog.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manages Server-Sent Event (SSE) connections for streaming build logs.
 * Multiple clients can subscribe to the same app's build log simultaneously.
 */
public interface BuildLogService {

    /**
     * Creates a new SSE connection for a client to receive build log events.
     *
     * @param appName the application to subscribe to
     * @return an SseEmitter that streams build log messages
     */
    SseEmitter subscribe(String appName);

    /**
     * Broadcasts a log message to all clients subscribed to the given app.
     *
     * @param appName the application whose subscribers should receive the message
     * @param message the log message to send
     */
    void send(String appName, String message);

    /**
     * Completes all SSE connections for the given app, signaling the build is done.
     *
     * @param appName the application whose build has finished
     */
    void complete(String appName);
}
