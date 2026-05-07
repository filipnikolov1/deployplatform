package dev.filipnikolov.vector.analyzer.commit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Analyzer-side resolver that combines a DB lookup ({@link #resolveForApp}) with URL parsing
 * delegated to {@link dev.filipnikolov.vector.github.repo.RepoSlugResolver} from
 * {@code vector-github}.
 *
 * <p>This class is retained as a Spring bean for the DB-backed {@link #resolveForApp}
 * convenience used by {@code CommitService} and {@code CrashAnalysisService}.
 * The old inline {@code parse()} implementation has been replaced by the canonical
 * {@link dev.filipnikolov.vector.github.repo.RepoSlugResolver#parse} from the shared module.
 */
@Component
public class RepoSlugResolver {

    private final JdbcTemplate jdbc;

    public RepoSlugResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Looks up the repo URL for {@code appName} in the DB and parses it into a slug string.
     *
     * @param appName application name
     * @return {@code "owner/repo"} slug, or {@code null} if not found / unparseable
     */
    public String resolveForApp(String appName) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT repo_url FROM public.deployment WHERE app_name = ? AND deleted_at IS NULL AND repo_url IS NOT NULL LIMIT 1",
                appName);
        if (rows.isEmpty()) return null;
        String repoUrl = (String) rows.get(0).get("repo_url");
        Optional<dev.filipnikolov.vector.github.repo.RepoSlug> slug =
                dev.filipnikolov.vector.github.repo.RepoSlugResolver.parse(repoUrl);
        return slug.map(dev.filipnikolov.vector.github.repo.RepoSlug::full).orElse(null);
    }
}
