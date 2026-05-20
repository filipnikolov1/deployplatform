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
            // Delete log entries older than retentionDays AND not from the last retentionDeploys per app.
            // ROW_NUMBER() partitions per app_name so we keep the latest N deploys per app —
            // the previous LIMIT ? form picked the top N globally, breaking the per-app guarantee.
            int deleted = jdbc.update("""
                    DELETE FROM analyzer.log_entry
                    WHERE timestamp < NOW() - INTERVAL '1 day' * ?
                      AND deployment_id NOT IN (
                          SELECT id FROM (
                              SELECT id, ROW_NUMBER() OVER (PARTITION BY app_name ORDER BY id DESC) AS rn
                              FROM public.deployment
                              WHERE deleted_at IS NULL
                          ) ranked
                          WHERE rn <= ?
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
