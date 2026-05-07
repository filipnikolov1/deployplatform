package dev.filipnikolov.vector.analyzer.github;

import dev.filipnikolov.vector.analyzer.timeline.TimelineEventService;
import dev.filipnikolov.vector.github.client.GitHubClient;
import dev.filipnikolov.vector.github.commit.CommitFetcher;
import dev.filipnikolov.vector.github.commit.CommitMetadata;
import dev.filipnikolov.vector.github.repo.RepoSlug;
import dev.filipnikolov.vector.github.repo.RepoSlugResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Periodically syncs recent commits from GitHub into {@code analyzer.commit_cache}
 * and the timeline, for all apps that have a configured repo URL.
 *
 * <p>Each sync populates {@code sha}, {@code parent_sha}, {@code author},
 * {@code authored_at}, and {@code message} — absorbing Track A3.2.
 */
@Component
public class GitHubCommitSyncJob {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommitSyncJob.class);
    private static final long RATE_LIMIT_INTERVAL_MS = 600; // ~100 calls/min

    private static final int SYNC_PER_PAGE = 30;
    private static final int SYNC_LOOKBACK_DAYS = 30;

    private final TimelineEventService timelineEventService;
    private final JdbcTemplate jdbc;
    private final String token;
    private final CommitFetcher commitFetcher;

    private long lastCallMs = 0;

    public GitHubCommitSyncJob(TimelineEventService timelineEventService,
                               JdbcTemplate jdbc,
                               @Value("${github.token:}") String token) {
        this.timelineEventService = timelineEventService;
        this.jdbc = jdbc;
        this.token = token;

        GitHubClient client = new GitHubClient(token);
        this.commitFetcher = new CommitFetcher(client);
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 5_000)
    public void sync() {
        if (token == null || token.isBlank()) {
            return;
        }

        List<Map<String, Object>> apps = jdbc.queryForList(
                "SELECT app_name, repo_url, branch FROM public.deployment WHERE deleted_at IS NULL AND repo_url IS NOT NULL");

        for (Map<String, Object> app : apps) {
            String appName = (String) app.get("app_name");
            String repoUrl = (String) app.get("repo_url");
            String branch  = (String) app.get("branch");

            Optional<RepoSlug> slugOpt = RepoSlugResolver.parse(repoUrl);
            if (slugOpt.isEmpty()) {
                log.debug("Skipping app {} — cannot parse repo URL: {}", appName, repoUrl);
                continue;
            }
            syncAppCommits(appName, slugOpt.get(), branch);
        }
    }

    private void syncAppCommits(String appName, RepoSlug slug, String branch) {
        try {
            rateLimitWait();

            Instant since = Instant.now().minusSeconds(SYNC_LOOKBACK_DAYS * 24L * 3600L);
            String effectiveBranch = (branch != null && !branch.isBlank()) ? branch : "HEAD";

            List<CommitMetadata> commits = commitFetcher.listRecent(slug, effectiveBranch, since, SYNC_PER_PAGE);

            for (CommitMetadata c : commits) {
                if (c.sha() == null) continue;

                // Upsert into commit_cache — populate parent_sha, author, authored_at, message
                LocalDateTime authoredAt = c.authoredAt() != null
                        ? LocalDateTime.ofInstant(c.authoredAt(), ZoneOffset.UTC)
                        : null;

                jdbc.update("""
                        INSERT INTO analyzer.commit_cache (repo_full_name, sha, parent_sha, author, authored_at, message)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (repo_full_name, sha) DO UPDATE
                            SET parent_sha  = EXCLUDED.parent_sha,
                                author      = EXCLUDED.author,
                                authored_at = EXCLUDED.authored_at,
                                message     = EXCLUDED.message
                        """,
                        slug.full(),
                        c.sha(),
                        c.parentSha(),
                        c.author(),
                        authoredAt,
                        c.message());

                // Also record in the timeline
                timelineEventService.insertCommitEvent(
                        appName,
                        c.sha(),
                        c.message()    != null ? c.message()    : "",
                        c.author()     != null ? c.author()     : "",
                        authoredAt     != null ? authoredAt     : LocalDateTime.now());
            }

        } catch (Exception e) {
            log.debug("GitHub commit sync skipped for {}/{}: {}", slug.owner(), slug.name(), e.getMessage());
        }
    }

    private synchronized void rateLimitWait() throws InterruptedException {
        long now = System.currentTimeMillis();
        long wait = RATE_LIMIT_INTERVAL_MS - (now - lastCallMs);
        if (wait > 0) Thread.sleep(wait);
        lastCallMs = System.currentTimeMillis();
    }
}
