package dev.filipnikolov.vector.github.scan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RepoScannerTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RepoScanner scanner;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        scanner = new RepoScanner(() -> "test-token", builder);
    }

    private String contentResponse(String content) {
        return "{\"content\":\"" + Base64.getEncoder().encodeToString(content.getBytes()) + "\"}";
    }

    @Test
    void scanReturnsManifestsAndComposeSignalSkippingIgnoredDirs() {
        server.expect(requestTo("https://api.github.com/repos/o/r/git/trees/main?recursive=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "tree": [
                            {"path": "apps/web/package.json", "type": "blob"},
                            {"path": "apps/api/go.mod", "type": "blob"},
                            {"path": "node_modules/x/package.json", "type": "blob"},
                            {"path": "docker-compose.yml", "type": "blob"}
                          ],
                          "truncated": false
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/o/r/contents/apps/web/package.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(contentResponse("{\"dependencies\":{}}"), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/o/r/contents/apps/api/go.mod?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(contentResponse("module example.com/api"), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/o/r/contents/docker-compose.yml?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(contentResponse("services:\n  db:\n    image: postgres"), MediaType.APPLICATION_JSON));

        RepoScan scan = scanner.scan("o", "r", "main");

        assertThat(scan.treePaths()).contains("apps/web/package.json", "apps/api/go.mod",
                "node_modules/x/package.json", "docker-compose.yml");
        assertThat(scan.truncated()).isFalse();
        assertThat(scan.manifests()).hasSize(2);
        assertThat(scan.manifests()).extracting(ManifestFile::path)
                .containsExactlyInAnyOrder("apps/web/package.json", "apps/api/go.mod");
        assertThat(scan.composeYaml()).isPresent();
        assertThat(scan.composeYaml().get()).contains("postgres");

        server.verify();
    }

    @Test
    void scanPropagatesTruncatedFlag() {
        server.expect(requestTo("https://api.github.com/repos/o/r/git/trees/main?recursive=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "tree": [
                            {"path": "package.json", "type": "blob"}
                          ],
                          "truncated": true
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/o/r/contents/package.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(contentResponse("{\"dependencies\":{}}"), MediaType.APPLICATION_JSON));

        RepoScan scan = scanner.scan("o", "r", "main");

        assertThat(scan.truncated()).isTrue();

        server.verify();
    }

    @Test
    void scanCapsManifestFetchesAt30() {
        String treeJson = "{\"tree\": [" + IntStream.rangeClosed(1, 31)
                .mapToObj(i -> "{\"path\": \"dir" + i + "/package.json\", \"type\": \"blob\"}")
                .collect(Collectors.joining(",")) + "], \"truncated\": false}";

        server.expect(requestTo("https://api.github.com/repos/o/r/git/trees/main?recursive=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(treeJson, MediaType.APPLICATION_JSON));

        IntStream.rangeClosed(1, 30).forEach(i ->
                server.expect(requestTo("https://api.github.com/repos/o/r/contents/dir" + i + "/package.json?ref=main"))
                        .andExpect(method(HttpMethod.GET))
                        .andRespond(withSuccess(contentResponse("{\"dependencies\":{}}"), MediaType.APPLICATION_JSON)));

        RepoScan scan = scanner.scan("o", "r", "main");

        assertThat(scan.manifests()).hasSize(30);

        server.verify();
    }
}
