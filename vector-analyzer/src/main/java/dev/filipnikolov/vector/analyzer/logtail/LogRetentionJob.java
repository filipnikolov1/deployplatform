package dev.filipnikolov.vector.analyzer.logtail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionJob.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;
    private final int retentionDeploys;

    public LogRetentionJob(JdbcTemplate jdbc,
                           @Value("${analyzer.retention.days:30}") int retentionDays,
                           @Value("${analyzer.retention.deploys:5}") int retentionDeploys) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
        this.retentionDeploys = retentionDeploys;
    }

    @Scheduled(fixedDelayString = "${analyzer.retention.interval-ms:21600000}")
    public void run() {
        try {
            // Delete log entries older than retentionDays AND not from the last retentionDeploys per app
            int deleted = jdbc.update("""
                    DELETE FROM analyzer.log_entry
                    WHERE id IN (
                        SELECT le.id FROM analyzer.log_entry le
                        WHERE le.timestamp < NOW() - INTERVAL '1 day' * ?
                          AND le.deployment_id NOT IN (
                              SELECT DISTINCT d.id
                              FROM public.deployment d
                              WHERE d.app_name = le.app_name
                                AND d.deleted_at IS NULL
                              ORDER BY d.id DESC
                              LIMIT ?
                          )
                    )
                    """, retentionDays, retentionDeploys);

            // Purge diff_cache older than 7 days
            int diffDeleted = jdbc.update(
                    "DELETE FROM analyzer.diff_cache WHERE cached_at < NOW() - INTERVAL '7 days'");

            if (deleted > 0 || diffDeleted > 0) {
                log.info("Retention: removed {} log entries, {} diff cache entries", deleted, diffDeleted);
            }
        } catch (Exception e) {
            log.error("Retention job failed: {}", e.getMessage());
        }
    }
}
