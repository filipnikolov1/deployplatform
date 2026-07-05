package dev.filipnikolov.vector.github.client;

import dev.filipnikolov.vector.github.client.dto.StackKind;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubAutomationClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GitHubAutomationClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubAutomationClient(() -> "test-token", builder);
    }

    @Test
    void putFileCreatesNewFileWhenNoneExists() {
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/.github/workflows/vector-deploy.yml?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        server.expect(requestTo("https://api.github.com/repos/o/r/contents/.github/workflows/vector-deploy.yml"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"message\":\"msg\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"branch\":\"main\"")))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.putFile("o", "r", ".github/workflows/vector-deploy.yml", "content".getBytes(), "msg", "main");

        server.verify();
    }

    @Test
    void putActionsSecretEncryptsWithRepoPublicKey() {
        var gen = new X25519KeyPairGenerator();
        gen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        var kp = gen.generateKeyPair();
        byte[] pub = ((X25519PublicKeyParameters) kp.getPublic()).getEncoded();
        String pubB64 = Base64.getEncoder().encodeToString(pub);

        server.expect(requestTo("https://api.github.com/repos/o/r/actions/secrets/public-key"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"key_id\":\"key-1\",\"key\":\"" + pubB64 + "\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/o/r/actions/secrets/VECTOR_HMAC_SECRET"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"key_id\":\"key-1\"")))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.putActionsSecret("o", "r", "VECTOR_HMAC_SECRET", "hunter2");

        server.verify();
    }

    @Test
    void dispatchWorkflowPostsRef() {
        server.expect(requestTo("https://api.github.com/repos/o/r/actions/workflows/vector-deploy.yml/dispatches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"ref\":\"main\"")))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.dispatchWorkflow("o", "r", "vector-deploy.yml", "main");

        server.verify();
    }

    @Test
    void recentRunsMapsWorkflowRuns() {
        server.expect(requestTo("https://api.github.com/repos/o/r/actions/workflows/vector-deploy.yml/runs"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"workflow_runs\":[{\"id\":1,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"https://github.com/o/r/actions/runs/1\"}]}", MediaType.APPLICATION_JSON));

        var runs = client.recentRuns("o", "r", "vector-deploy.yml");

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).id()).isEqualTo(1L);
        assertThat(runs.get(0).status()).isEqualTo("completed");
        assertThat(runs.get(0).conclusion()).isEqualTo("success");
        assertThat(runs.get(0).htmlUrl()).isEqualTo("https://github.com/o/r/actions/runs/1");

        server.verify();
    }

    @Test
    void detectStackReturnsGoWhenGoModPresent() {
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/go.mod?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"go.mod\"}", MediaType.APPLICATION_JSON));

        StackKind kind = client.detectStack("o", "r", "main");

        assertThat(kind).isEqualTo(StackKind.go);
        server.verify();
    }

    @Test
    void detectStackReturnsNextjsWhenPackageJsonHasNextDependency() {
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/go.mod?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/package.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"content\":\"" + Base64.getEncoder().encodeToString(
                                "{\"dependencies\":{\"next\":\"14.0.0\"}}".getBytes()) + "\"}",
                        MediaType.APPLICATION_JSON));

        StackKind kind = client.detectStack("o", "r", "main");

        assertThat(kind).isEqualTo(StackKind.nextjs);
        server.verify();
    }

    @Test
    void detectStackReturnsNodeWhenPackageJsonHasNoNextDependency() {
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/go.mod?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/package.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"content\":\"" + Base64.getEncoder().encodeToString(
                                "{\"dependencies\":{\"express\":\"4.0.0\"}}".getBytes()) + "\"}",
                        MediaType.APPLICATION_JSON));

        StackKind kind = client.detectStack("o", "r", "main");

        assertThat(kind).isEqualTo(StackKind.node);
        server.verify();
    }

    @Test
    void detectStackReturnsSpringbootWhenPomXmlPresent() {
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/go.mod?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/package.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/pom.xml?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"pom.xml\"}", MediaType.APPLICATION_JSON));

        StackKind kind = client.detectStack("o", "r", "main");

        assertThat(kind).isEqualTo(StackKind.springboot);
        server.verify();
    }

    @Test
    void detectStackReturnsPythonWhenRequirementsTxtPresent() {
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/go.mod?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/package.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/pom.xml?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/requirements.txt?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"requirements.txt\"}", MediaType.APPLICATION_JSON));

        StackKind kind = client.detectStack("o", "r", "main");

        assertThat(kind).isEqualTo(StackKind.python);
        server.verify();
    }

    @Test
    void detectStackReturnsCustomWhenOnlyDockerfilePresent() {
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/go.mod?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/package.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/pom.xml?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/requirements.txt?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/repos/o/r/contents/Dockerfile?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"Dockerfile\"}", MediaType.APPLICATION_JSON));

        StackKind kind = client.detectStack("o", "r", "main");

        assertThat(kind).isEqualTo(StackKind.custom);
        server.verify();
    }
}
