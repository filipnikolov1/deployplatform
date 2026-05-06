package dev.filipnikolov.vector.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int WINDOW_MS = 60_000;
    private static final int MAX_DEPLOY_HOOK_REQUESTS = 30;

    private final ConcurrentHashMap<String, long[]> requestCounts = new ConcurrentHashMap<>();
    private final List<String> trustedProxies;

    public RateLimitFilter(
            @Value("${vector.security.trusted-proxies:}") String trustedProxiesConfig) {
        this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Only rate-limit the public deploy-hook surface. Authenticated /api/* traffic is
        // gated by ApiKeyAuthFilter and would self-DoS here since the BFF funnels every
        // dashboard session through a single source IP.
        if (!request.getRequestURI().startsWith("/deploy-hook")) {
            chain.doFilter(request, response);
            return;
        }

        String key = resolveClientIp(request) + ":deploy-hook";
        long now = System.currentTimeMillis();
        long[] entry = requestCounts.compute(key, (k, v) -> {
            if (v == null || now - v[1] > WINDOW_MS) {
                return new long[]{1, now};
            }
            v[0]++;
            return v;
        });

        if (entry[0] > MAX_DEPLOY_HOOK_REQUESTS) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\"}");
            return;
        }

        chain.doFilter(request, response);

        cleanup();
    }

    /**
     * Resolves the originating client IP.
     *
     * <p>If {@code X-Forwarded-For} is present AND the direct connection peer
     * ({@code remoteAddr}) is in the configured trusted-proxy list, the first
     * entry of the XFF header is used (the origin client). Otherwise falls back
     * to {@code remoteAddr} to avoid IP spoofing via a forged XFF header.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }
        // XFF may be a comma-separated list; take the leftmost (origin client).
        String firstEntry = xff.split(",")[0].trim();
        return firstEntry.isEmpty() ? remoteAddr : firstEntry;
    }

    private void cleanup() {
        if (requestCounts.size() > 10_000) {
            long now = System.currentTimeMillis();
            requestCounts.entrySet().removeIf(e -> now - e.getValue()[1] > WINDOW_MS * 5);
        }
    }
}
