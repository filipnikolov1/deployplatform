package dev.filipnikolov.vector.sse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PostgresListenService {

    private static final Logger log = LoggerFactory.getLogger(PostgresListenService.class);
    private static final String CHANNEL = "deployment_events";
    private static final long HEARTBEAT_INTERVAL_MS = 15_000;
    private static final long HEALTH_CHECK_INTERVAL_MS = 30_000;
    private static final int NOTIFY_POLL_TIMEOUT_MS = 1_000;

    private final DataSource dataSource;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private volatile Connection listenConnection;
    private volatile boolean running = true;

    public PostgresListenService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void start() {
        Thread t = new Thread(this::listenLoop, "pg-listen");
        t.setDaemon(true);
        t.start();
    }

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ignored -> emitters.remove(emitter));
        return emitter;
    }

    private void listenLoop() {
        long lastHeartbeat = System.currentTimeMillis();
        long lastHealthCheck = System.currentTimeMillis();

        while (running) {
            try {
                if (listenConnection == null || listenConnection.isClosed()) {
                    connect();
                }

                PGConnection pg = listenConnection.unwrap(PGConnection.class);
                PGNotification[] notifications = pg.getNotifications(NOTIFY_POLL_TIMEOUT_MS);

                if (notifications != null) {
                    for (PGNotification n : notifications) {
                        broadcast(n.getParameter());
                    }
                }

                long now = System.currentTimeMillis();

                if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                    sendHeartbeat();
                    lastHeartbeat = now;
                }

                if (now - lastHealthCheck >= HEALTH_CHECK_INTERVAL_MS) {
                    healthCheck();
                    lastHealthCheck = now;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("pg-listen error, reconnecting in 2s: {}", e.getMessage());
                closeConnection();
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void connect() throws Exception {
        listenConnection = dataSource.getConnection();
        try (Statement stmt = listenConnection.createStatement()) {
            stmt.execute("LISTEN " + CHANNEL);
        }
        log.info("LISTEN connected on channel '{}'", CHANNEL);
    }

    private void broadcast(String payload) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("deployment-event").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    private void sendHeartbeat() {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    private void healthCheck() {
        try (Statement stmt = listenConnection.createStatement()) {
            stmt.execute("SELECT 1");
            log.debug("pg-listen health check passed, {} clients connected", emitters.size());
        } catch (Exception e) {
            log.warn("pg-listen health check failed, reconnecting");
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (listenConnection != null && !listenConnection.isClosed()) {
                listenConnection.close();
            }
        } catch (Exception ignored) {
        }
        listenConnection = null;
    }

    @PreDestroy
    public void stop() {
        running = false;
        closeConnection();
    }
}
