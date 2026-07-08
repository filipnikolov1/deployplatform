package dev.filipnikolov.vector.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainConfigTest {

    private DomainConfig config(String domain, String ns, String web, String api) {
        return new DomainConfig(domain, ns, web, api);
    }

    @Test
    void localhostDefaultsPreserveDevScheme() {
        DomainConfig c = config("localhost", "apps", "", "");
        assertThat(c.dashboardHost()).isEqualTo("deploy.localhost");
        assertThat(c.apiHost()).isEqualTo("api.deploy.localhost");
        assertThat(c.appDomain()).isEqualTo("apps.localhost");
        assertThat(c.appHost("myapp")).isEqualTo("myapp.apps.localhost");
        assertThat(c.publicBaseUrl()).isEqualTo("http://localhost:8082");
        assertThat(c.dashboardBaseUrl()).isEqualTo("http://localhost:3000");
    }

    @Test
    void prodDomainDerivesFullScheme() {
        DomainConfig c = config("filipnikolov.dev", "apps", "", "");
        assertThat(c.dashboardHost()).isEqualTo("deploy.filipnikolov.dev");
        assertThat(c.apiHost()).isEqualTo("api.deploy.filipnikolov.dev");
        assertThat(c.appHost("myapp")).isEqualTo("myapp.apps.filipnikolov.dev");
        assertThat(c.publicBaseUrl()).isEqualTo("https://api.deploy.filipnikolov.dev");
        assertThat(c.dashboardBaseUrl()).isEqualTo("https://deploy.filipnikolov.dev");
    }

    @Test
    void blankNamespacePutsAppsOnRootDomain() {
        DomainConfig c = config("filipnikolov.dev", "", "", "");
        assertThat(c.appDomain()).isEqualTo("filipnikolov.dev");
        assertThat(c.appHost("myapp")).isEqualTo("myapp.filipnikolov.dev");
    }

    @Test
    void customNamespaceIsHonored() {
        DomainConfig c = config("filipnikolov.dev", "preview", "", "");
        assertThat(c.appHost("myapp")).isEqualTo("myapp.preview.filipnikolov.dev");
    }

    @Test
    void perHostOverridesWin() {
        DomainConfig c = config("filipnikolov.dev", "apps",
                "dash.example.com", "backend.example.com");
        assertThat(c.dashboardHost()).isEqualTo("dash.example.com");
        assertThat(c.apiHost()).isEqualTo("backend.example.com");
        assertThat(c.publicBaseUrl()).isEqualTo("https://backend.example.com");
        // overrides never touch app hosts
        assertThat(c.appHost("myapp")).isEqualTo("myapp.apps.filipnikolov.dev");
    }

    @Test
    void blankDomainFallsBackToLocalhost() {
        DomainConfig c = config("  ", "apps", "", "");
        assertThat(c.dashboardHost()).isEqualTo("deploy.localhost");
        assertThat(c.publicBaseUrl()).isEqualTo("http://localhost:8082");
    }
}
