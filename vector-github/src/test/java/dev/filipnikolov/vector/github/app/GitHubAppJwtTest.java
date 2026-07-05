package dev.filipnikolov.vector.github.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAppJwtTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void createProducesValidSignedJwt() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        Instant now = Instant.parse("2026-07-04T12:00:00Z");
        String jwt = GitHubAppJwt.create("12345", privateKey, now);

        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        JsonNode header = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");

        JsonNode payload = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload.get("iss").asText()).isEqualTo("12345");
        assertThat(payload.get("iat").asLong()).isEqualTo(now.getEpochSecond() - 60);
        assertThat(payload.get("exp").asLong()).isEqualTo(now.getEpochSecond() + 540);

        String signingInput = parts[0] + "." + parts[1];
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(signingInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    void parsePrivateKeyRoundTripsPkcs8Pem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";

        PrivateKey parsed = GitHubAppJwt.parsePrivateKey(pem);

        assertThat(parsed).isEqualTo(privateKey);
    }
}
