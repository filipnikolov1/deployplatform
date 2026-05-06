package dev.filipnikolov.vector.analyzer.commit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class RepoSlugResolver {

    private final JdbcTemplate jdbc;

    public RepoSlugResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String resolveForApp(String appName) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT repo_url FROM public.deployment WHERE app_name = ? AND deleted_at IS NULL AND repo_url IS NOT NULL LIMIT 1",
                appName);
        if (rows.isEmpty()) return null;
        return parse((String) rows.get(0).get("repo_url"));
    }

    static String parse(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        String slug = null;

        if (trimmed.startsWith("git@github.com:")) {
            slug = trimmed.substring("git@github.com:".length());
        } else {
            try {
                URI uri = URI.create(trimmed);
                if (!"github.com".equalsIgnoreCase(uri.getHost())) return null;
                slug = uri.getPath();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        if (slug == null) return null;
        slug = slug.strip();
        while (slug.startsWith("/")) slug = slug.substring(1);
        while (slug.endsWith("/")) slug = slug.substring(0, slug.length() - 1);
        if (slug.endsWith(".git")) slug = slug.substring(0, slug.length() - 4);

        String[] parts = slug.split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) return null;
        return parts[0] + "/" + parts[1];
    }
}
