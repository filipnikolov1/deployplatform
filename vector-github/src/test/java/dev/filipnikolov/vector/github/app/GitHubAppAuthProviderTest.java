package dev.filipnikolov.vector.github.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitHubAppAuthProviderTest {

    private PrivateKey privateKey;
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = keyPair.getPrivate();

        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void tokenForFetchesAndCachesInstallationToken() {
        String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        server.expect(requestTo("https://api.github.com/app/installations/77/access_tokens"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(request -> assertThat(request.getHeaders().getFirst("Authorization")).startsWith("Bearer "))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"token\":\"ghs_abc\",\"expires_at\":\"" + expiresAt + "\"}"));

        GitHubAppAuthProvider provider = new GitHubAppAuthProvider("12345", privateKey, builder);

        String token = provider.tokenFor(77);
        assertThat(token).isEqualTo("ghs_abc");

        // second call should hit the cache, no additional HTTP request expected
        String cached = provider.tokenFor(77);
        assertThat(cached).isEqualTo("ghs_abc");

        server.verify();
    }

    @Test
    void tokenForRefetchesWhenNearExpiry() {
        String almostExpired = Instant.now().plus(3, ChronoUnit.MINUTES).toString();
        String fresh = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        server.expect(requestTo("https://api.github.com/app/installations/77/access_tokens"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"token\":\"ghs_old\",\"expires_at\":\"" + almostExpired + "\"}"));
        server.expect(requestTo("https://api.github.com/app/installations/77/access_tokens"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"token\":\"ghs_new\",\"expires_at\":\"" + fresh + "\"}"));

        GitHubAppAuthProvider provider = new GitHubAppAuthProvider("12345", privateKey, builder);

        String first = provider.tokenFor(77);
        assertThat(first).isEqualTo("ghs_old");

        String second = provider.tokenFor(77);
        assertThat(second).isEqualTo("ghs_new");

        server.verify();
    }

    @Test
    void appInfoMapsGetAppResponse() {
        server.expect(requestTo("https://api.github.com/app"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"slug\":\"my-deploy-app\",\"owner\":{\"login\":\"filipnikolov1\"}}"));

        GitHubAppAuthProvider provider = new GitHubAppAuthProvider("12345", privateKey, builder);

        GitHubAppAuthProvider.AppInfo info = provider.appInfo();
        assertThat(info.slug()).isEqualTo("my-deploy-app");
        assertThat(info.ownerLogin()).isEqualTo("filipnikolov1");

        server.verify();
    }
}
