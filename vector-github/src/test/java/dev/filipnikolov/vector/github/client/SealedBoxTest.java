package dev.filipnikolov.vector.github.client;

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;

class SealedBoxTest {
    @Test
    void seal_then_open_round_trips() {
        var gen = new X25519KeyPairGenerator();
        gen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        var kp = gen.generateKeyPair();
        byte[] pub = ((X25519PublicKeyParameters) kp.getPublic()).getEncoded();
        byte[] priv = ((X25519PrivateKeyParameters) kp.getPrivate()).getEncoded();

        String secret = "hunter2";
        String sealedB64 = SealedBox.seal(secret, Base64.getEncoder().encodeToString(pub));
        byte[] opened = SealedBox.open(Base64.getDecoder().decode(sealedB64), pub, priv);

        assertThat(new String(opened, StandardCharsets.UTF_8)).isEqualTo(secret);
    }
}
