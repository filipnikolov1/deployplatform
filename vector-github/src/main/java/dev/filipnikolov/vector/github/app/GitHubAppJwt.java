package dev.filipnikolov.vector.github.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

/**
 * Produces RS256-signed GitHub App JWTs using {@link java.security.Signature} directly — no
 * JWT library dependency.
 */
public final class GitHubAppJwt {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private GitHubAppJwt() {
    }

    /**
     * Creates an RS256-signed App JWT.
     *
     * @param appId GitHub App ID (becomes the {@code iss} claim)
     * @param key   App private key
     * @param now   current instant; {@code iat} is set 60s in the past and {@code exp} 540s
     *              (9 min) in the future to tolerate clock drift while staying under GitHub's
     *              10-minute cap
     * @return the signed JWT in {@code header.payload.signature} form
     */
    public static String create(String appId, PrivateKey key, Instant now) {
        try {
            ObjectNode header = MAPPER.createObjectNode();
            header.put("alg", "RS256");
            header.put("typ", "JWT");

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("iss", appId);
            payload.put("iat", now.getEpochSecond() - 60);
            payload.put("exp", now.getEpochSecond() + 540);

            String encodedHeader = base64UrlEncode(MAPPER.writeValueAsBytes(header));
            String encodedPayload = base64UrlEncode(MAPPER.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = base64UrlEncode(signature.sign());

            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create GitHub App JWT", e);
        }
    }

    /**
     * Parses a PEM-encoded RSA private key, accepting both PKCS#8
     * ({@code -----BEGIN PRIVATE KEY-----}) and PKCS#1
     * ({@code -----BEGIN RSA PRIVATE KEY-----}, as downloaded from GitHub) forms.
     *
     * @param pem PEM-encoded private key
     * @return parsed {@link PrivateKey}
     */
    public static PrivateKey parsePrivateKey(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object parsed = parser.readObject();
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                PrivateKeyInfo privateKeyInfo = pemKeyPair.getPrivateKeyInfo();
                return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
            }
            if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
            }
            throw new IllegalArgumentException("Unsupported PEM content: " + (parsed == null ? "null" : parsed.getClass()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse private key PEM", e);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }
}
