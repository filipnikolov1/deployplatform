package dev.filipnikolov.vector.github.client;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Pure-Java implementation of libsodium's {@code crypto_box_seal}/{@code crypto_box_seal_open}
 * (anonymous sealed box) built on BouncyCastle primitives, so Actions secrets can be encrypted
 * without a native libsodium dependency.
 */
public final class SealedBox {

    private static final int KEY_LENGTH = 32;
    private static final int NONCE_LENGTH = 24;
    private static final int MAC_LENGTH = 16;

    private SealedBox() {
    }

    public static String seal(String plaintext, String recipientPublicKeyBase64) {
        byte[] recipientPub = Base64.getDecoder().decode(recipientPublicKeyBase64);

        X25519KeyPairGenerator gen = new X25519KeyPairGenerator();
        gen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        var ephemeralKeyPair = gen.generateKeyPair();
        byte[] ephemeralPub = ((X25519PublicKeyParameters) ephemeralKeyPair.getPublic()).getEncoded();
        X25519PrivateKeyParameters ephemeralPriv = (X25519PrivateKeyParameters) ephemeralKeyPair.getPrivate();

        byte[] sharedSecret = agree(ephemeralPriv, recipientPub);
        byte[] nonce = nonce(ephemeralPub, recipientPub);
        byte[] ciphertext = box(sharedSecret, nonce, plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] sealed = new byte[ephemeralPub.length + ciphertext.length];
        System.arraycopy(ephemeralPub, 0, sealed, 0, ephemeralPub.length);
        System.arraycopy(ciphertext, 0, sealed, ephemeralPub.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(sealed);
    }

    public static byte[] open(byte[] sealed, byte[] recipientPublicKey, byte[] recipientPrivateKey) {
        byte[] ephemeralPub = Arrays.copyOfRange(sealed, 0, KEY_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(sealed, KEY_LENGTH, sealed.length);

        X25519PrivateKeyParameters recipientPriv = new X25519PrivateKeyParameters(recipientPrivateKey, 0);
        byte[] sharedSecret = agree(recipientPriv, ephemeralPub);
        byte[] nonce = nonce(ephemeralPub, recipientPublicKey);

        return openBox(sharedSecret, nonce, ciphertext);
    }

    private static byte[] agree(X25519PrivateKeyParameters privateKey, byte[] publicKey) {
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(privateKey);
        byte[] shared = new byte[agreement.getAgreementSize()];
        agreement.calculateAgreement(new X25519PublicKeyParameters(publicKey, 0), shared, 0);
        return shared;
    }

    private static byte[] nonce(byte[] ephemeralPub, byte[] recipientPub) {
        Blake2bDigest digest = new Blake2bDigest(NONCE_LENGTH * 8);
        digest.update(ephemeralPub, 0, ephemeralPub.length);
        digest.update(recipientPub, 0, recipientPub.length);
        byte[] out = new byte[NONCE_LENGTH];
        digest.doFinal(out, 0);
        return out;
    }

    private static byte[] box(byte[] sharedSecret, byte[] nonce, byte[] message) {
        XSalsa20Engine engine = new XSalsa20Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(sharedSecret), nonce));

        byte[] macKey = new byte[KEY_LENGTH];
        engine.processBytes(new byte[KEY_LENGTH], 0, KEY_LENGTH, macKey, 0);

        byte[] ciphertext = new byte[message.length];
        engine.processBytes(message, 0, message.length, ciphertext, 0);

        Poly1305 poly1305 = new Poly1305();
        poly1305.init(new KeyParameter(macKey));
        poly1305.update(ciphertext, 0, ciphertext.length);
        byte[] mac = new byte[MAC_LENGTH];
        poly1305.doFinal(mac, 0);

        byte[] result = new byte[MAC_LENGTH + ciphertext.length];
        System.arraycopy(mac, 0, result, 0, MAC_LENGTH);
        System.arraycopy(ciphertext, 0, result, MAC_LENGTH, ciphertext.length);
        return result;
    }

    private static byte[] openBox(byte[] sharedSecret, byte[] nonce, byte[] boxed) {
        byte[] mac = Arrays.copyOfRange(boxed, 0, MAC_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(boxed, MAC_LENGTH, boxed.length);

        XSalsa20Engine engine = new XSalsa20Engine();
        engine.init(false, new ParametersWithIV(new KeyParameter(sharedSecret), nonce));

        byte[] macKey = new byte[KEY_LENGTH];
        engine.processBytes(new byte[KEY_LENGTH], 0, KEY_LENGTH, macKey, 0);

        Poly1305 poly1305 = new Poly1305();
        poly1305.init(new KeyParameter(macKey));
        poly1305.update(ciphertext, 0, ciphertext.length);
        byte[] expectedMac = new byte[MAC_LENGTH];
        poly1305.doFinal(expectedMac, 0);

        if (!Arrays.equals(mac, expectedMac)) {
            throw new IllegalArgumentException("SealedBox: MAC verification failed");
        }

        byte[] plaintext = new byte[ciphertext.length];
        engine.processBytes(ciphertext, 0, ciphertext.length, plaintext, 0);
        return plaintext;
    }
}
