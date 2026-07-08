package dev.filipnikolov.vector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Derives every public hostname of the platform from VECTOR_DOMAIN (+ optional
 * VECTOR_APP_NAMESPACE), per the env-var-simplification spec:
 *
 *   dashboard        deploy.<domain>
 *   API/deploy-hook  api.deploy.<domain>
 *   user apps        <app>.<namespace>.<domain>   (<app>.<domain> when namespace blank)
 *
 * VECTOR_DOMAIN_WEB / VECTOR_DOMAIN_API are per-host escape hatches over the scheme.
 * VECTOR_DOMAIN=localhost (local dev) keeps today's *.localhost routing and the
 * bare-JVM public base URL http://localhost:8082.
 */
@Component
public class DomainConfig {

    private final String domain;
    private final String appNamespace;
    private final String webHostOverride;
    private final String apiHostOverride;

    public DomainConfig(
            @Value("${vector.domain:localhost}") String domain,
            @Value("${vector.app-namespace:apps}") String appNamespace,
            @Value("${vector.domain.web:}") String webHostOverride,
            @Value("${vector.domain.api:}") String apiHostOverride) {
        this.domain = (domain == null || domain.isBlank()) ? "localhost" : domain.trim();
        this.appNamespace = appNamespace == null ? "" : appNamespace.trim();
        this.webHostOverride = webHostOverride == null ? "" : webHostOverride.trim();
        this.apiHostOverride = apiHostOverride == null ? "" : apiHostOverride.trim();
    }

    public boolean isLocal() {
        return "localhost".equals(domain);
    }

    /** deploy.<domain>, unless VECTOR_DOMAIN_WEB overrides. */
    public String dashboardHost() {
        return webHostOverride.isBlank() ? "deploy." + domain : webHostOverride;
    }

    /** api.deploy.<domain>, unless VECTOR_DOMAIN_API overrides. */
    public String apiHost() {
        return apiHostOverride.isBlank() ? "api.deploy." + domain : apiHostOverride;
    }

    /** Base domain user apps hang off: <namespace>.<domain>, or <domain> when namespace is blank. */
    public String appDomain() {
        return appNamespace.isBlank() ? domain : appNamespace + "." + domain;
    }

    /** Full public host for a deployed app's effective subdomain. */
    public String appHost(String effectiveSubdomain) {
        return effectiveSubdomain + "." + appDomain();
    }

    /** Public base URL of vector-api (webhook templates, setup page). */
    public String publicBaseUrl() {
        return isLocal() ? "http://localhost:8082" : "https://" + apiHost();
    }

    /** Public base URL of the dashboard (GitHub App manifest flow, redirect URLs). */
    public String dashboardBaseUrl() {
        return isLocal() ? "http://localhost:3000" : "https://" + dashboardHost();
    }
}
