package dev.filipnikolov.vector.analyzer.listener;

import dev.filipnikolov.vector.analyzer.logtail.LogTailService;
import dev.filipnikolov.vector.analyzer.timeline.TimelineEventService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PostgresEventListener {

    private static final Logger log = LoggerFactory.getLogger(PostgresEventListener.class);

    private final DataSource dataSource;
    private final TimelineEventService timelineEventService;
    private final LogTailService logTailService;

    @Value("${analyzer.pg.listen-channel:deployment_events}")
    private String channel;

    private volatile Connection listenConn;
    private volatile Thread listenThread;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final ScheduledExecutorService healthScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "analyzer-listen-health");
                t.setDaemon(true);
                return t;
            });

    public PostgresEventListener(DataSource dataSource,
                                 TimelineEventService timelineEventService,
                                 LogTailService logTailService) {
        this.dataSource = dataSource;
        this.timelineEventService = timelineEventService;
        this.logTailService = logTailService;
    }

    @PostConstruct
    public void init() {
        // Backfill / catch-up before opening the LISTEN connection so the cursor
        // is at the latest event ID when we start receiving live notifications.
        timelineEventService.performStartupSync();
        connect();
        healthScheduler.scheduleWithFixedDelay(this::healthCheck, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        shutdown.set(true);
        healthScheduler.shutdownNow();
        closeQuietly(listenConn);
    }

    private void connect() {
        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(true);
            try (Statement s = conn.createStatement()) {
                s.execute("LISTEN " + channel);
            }
            listenConn = conn;

            Thread t = new Thread(() -> listenLoop(conn), "analyzer-listen-loop");
            t.setDaemon(true);
            t.start();
            listenThread = t;

            log.info("LISTEN connection established on channel '{}'", channel);
        } catch (Exception e) {
            log.error("Failed to open LISTEN connection: {}", e.getMessage());
        }
    }

    private void listenLoop(Connection conn) {
        try {
            while (!shutdown.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // A dummy query is required to flush pending notifications from the socket.
                    try (Statement s = conn.createStatement()) {
                        s.execute("SELECT 1");
                    }
                    PGNotification[] notifications = conn.unwrap(PGConnection.class).getNotifications();
                    if (notifications != null) {
                        for (PGNotification n : notifications) {
                            String payload = n.getParameter();
                            TimelineEventService.ProcessedDeploymentEvent event =
                                    timelineEventService.processNotification(payload);
                            maybeRestartLogTail(event);
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    if (!shutdown.get()) {
                        log.error("LISTEN loop error (will reconnect): {}", e.getMessage());
                    }
                    return;
                }
            }
        } finally {
            // Always close the connection — without this, exiting on error leaked the
            // PG connection until JVM restart, eventually exhausting the pool.
            closeQuietly(conn);
        }
    }

    private void healthCheck() {
        if (shutdown.get()) return;
        if (listenThread == null || !listenThread.isAlive()) {
            log.warn("LISTEN thread is dead — reconnecting and catching up");
            closeQuietly(listenConn);
            timelineEventService.catchUpSync();
            connect();
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
    }

    void maybeRestartLogTail(TimelineEventService.ProcessedDeploymentEvent event) {
        if (event == null || event.appName() == null || !"SUCCESS".equals(event.status())) return;
        if ("DEPLOY_FINISHED".equals(event.eventType())
                || "RESTARTED".equals(event.eventType())
                || "MANUAL_ROLLBACK".equals(event.eventType())) {
            logTailService.onDeployFinished(event.appName());
        }
    }
}
