package dev.filipnikolov.vector.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final int WINDOW_MS = 60_000;
    private static final int MAX_DEPLOY_HOOK_REQUESTS = 30;
    private static final int MAX_API_REQUESTS = 60;

    private final ConcurrentHashMap<String, long[]> requestCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        String path = request.getRequestURI();
        boolean isDeployHook = path.startsWith("/deploy-hook");
        int limit = isDeployHook ? MAX_DEPLOY_HOOK_REQUESTS : MAX_API_REQUESTS;
        String key = ip + ":" + (isDeployHook ? "deploy-hook" : "api");

        long now = System.currentTimeMillis();
        long[] entry = requestCounts.compute(key, (k, v) -> {
            if (v == null || now - v[1] > WINDOW_MS) {
                return new long[]{1, now};
            }
            v[0]++;
            return v;
        });

        if (entry[0] > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\"}");
            return;
        }

        chain.doFilter(request, response);

        cleanup();
    }

    private void cleanup() {
        if (requestCounts.size() > 10_000) {
            long now = System.currentTimeMillis();
            requestCounts.entrySet().removeIf(e -> now - e.getValue()[1] > WINDOW_MS * 5);
        }
    }
}
