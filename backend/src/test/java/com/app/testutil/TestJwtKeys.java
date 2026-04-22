package com.app.testutil;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/** PEM PKCS8 / SPKI wrapped in an outer Base64 layer, matching {@code PemKeys} and app config. */
public final class TestJwtKeys {

    private TestJwtKeys() {}

    public static KeyPair generateRsa2048() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    public static String pemPrivateKeyB64Layer(RSAPrivateKey priv) throws Exception {
        byte[] der = priv.getEncoded();
        String inner = Base64.getEncoder().encodeToString(der);
        String pem =
                "-----BEGIN PRIVATE KEY-----\n"
                        + inner.replaceAll("(.{64})", "$1\n")
                        + "\n-----END PRIVATE KEY-----";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }

    public static String pemPublicKeyB64Layer(RSAPublicKey pub) throws Exception {
        byte[] der = pub.getEncoded();
        String inner = Base64.getEncoder().encodeToString(der);
        String pem =
                "-----BEGIN PUBLIC KEY-----\n"
                        + inner.replaceAll("(.{64})", "$1\n")
                        + "\n-----END PUBLIC KEY-----";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }
}
