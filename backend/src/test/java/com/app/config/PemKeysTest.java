package com.app.config;

import com.app.testutil.TestJwtKeys;
import org.junit.jupiter.api.Test;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PemKeysTest {

    @Test
    void roundTrip_layeredPemAsInTests() throws Exception {
        var kp = TestJwtKeys.generateRsa2048();
        String privB64 = TestJwtKeys.pemPrivateKeyB64Layer((RSAPrivateKey) kp.getPrivate());
        String pubB64 = TestJwtKeys.pemPublicKeyB64Layer((RSAPublicKey) kp.getPublic());

        RSAPrivateKey priv = PemKeys.readPrivateKeyFromPemBase64(privB64);
        RSAPublicKey pub = PemKeys.readPublicKeyFromPemBase64(pubB64);

        assertNotNull(priv);
        assertNotNull(pub);
        assertEquals(kp.getPrivate(), priv);
        assertEquals(kp.getPublic(), pub);
    }

    @Test
    void acceptsPemWithExtraNewlinesInOuterBase64() throws Exception {
        var kp = TestJwtKeys.generateRsa2048();
        String oneLine = TestJwtKeys.pemPrivateKeyB64Layer((RSAPrivateKey) kp.getPrivate());
        String withBreaks = oneLine.replaceAll("(.{80})", "$1\n");

        RSAPrivateKey priv = PemKeys.readPrivateKeyFromPemBase64(withBreaks);
        assertEquals(kp.getPrivate(), priv);
    }
}
