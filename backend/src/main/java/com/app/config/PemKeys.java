package com.app.config;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Charge les clés RSA depuis {@code app.jwt.*-key-b64}.
 *
 * <p>Formats acceptés :
 * <ul>
 *   <li>Base64 standard / MIME / URL-safe du fichier PEM complet (comportement historique)</li>
 *   <li>PEM multi-ligne collé tel quel (sans couche base64 externe)</li>
 *   <li>Base64 du seul bloc PKCS#8 / SPKI (DER), sans en-têtes PEM</li>
 * </ul>
 */
public final class PemKeys {

    private PemKeys() {}

    public static RSAPrivateKey readPrivateKeyFromPemBase64(String raw) throws Exception {
        String input = normalizeKeyEnv(raw);
        if (input.startsWith("-----BEGIN")) {
            return parsePrivateKeyFromPemText(input);
        }
        byte[] layer1 = decodeBase64Flexible(input);
        if (layer1.length > 0 && layer1[0] == (byte) 0x30) {
            try {
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(layer1);
                return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (Exception ignored) {
                // pas du PKCS#8 brut, tenter comme texte PEM
            }
        }
        String pem = new String(layer1, StandardCharsets.UTF_8);
        if (pem.contains("BEGIN PRIVATE KEY")) {
            return parsePrivateKeyFromPemText(pem);
        }
        throw new IllegalArgumentException(
                "JWT private key: format non reconnu. Utilisez le PEM complet ou "
                        + "openssl base64 -A -in private.pem (puis une seule ligne dans .env).");
    }

    public static RSAPublicKey readPublicKeyFromPemBase64(String raw) throws Exception {
        String input = normalizeKeyEnv(raw);
        if (input.startsWith("-----BEGIN")) {
            return parsePublicKeyFromPemText(input);
        }
        byte[] layer1 = decodeBase64Flexible(input);
        if (layer1.length > 0 && layer1[0] == (byte) 0x30) {
            try {
                X509EncodedKeySpec spec = new X509EncodedKeySpec(layer1);
                return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (Exception ignored) {
            }
        }
        String pem = new String(layer1, StandardCharsets.UTF_8);
        if (pem.contains("BEGIN PUBLIC KEY")) {
            return parsePublicKeyFromPemText(pem);
        }
        throw new IllegalArgumentException(
                "JWT public key: format non reconnu. Vérifiez JWT_PUBLIC_KEY_B64 (voir .env.example).");
    }

    private static String normalizeKeyEnv(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("JWT key (app.jwt.private-key-b64 / public-key-b64) est vide.");
        }
        String s = raw.trim();
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim();
        }
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * Décode une chaîne base64 : tolère les retours à la ligne (MIME), le base64 « compact », et l’URL-safe.
     */
    private static byte[] decodeBase64Flexible(String s) {
        String compact = s.replaceAll("\\s+", "");
        IllegalArgumentException last = null;
        for (Base64.Decoder decoder :
                new Base64.Decoder[] {
                    Base64.getMimeDecoder(), Base64.getDecoder(), Base64.getUrlDecoder()
                }) {
            try {
                return decoder.decode(s);
            } catch (IllegalArgumentException e) {
                last = e;
            }
            try {
                return decoder.decode(compact);
            } catch (IllegalArgumentException e) {
                last = e;
            }
        }
        throw new IllegalArgumentException(
                "Base64 invalide pour une clé JWT (retours ligne, caractères spéciaux ou guillemets dans .env ?).",
                last);
    }

    private static RSAPrivateKey parsePrivateKeyFromPemText(String pem) throws Exception {
        String cleaned =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] pkcs8 = decodeBase64Flexible(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static RSAPublicKey parsePublicKeyFromPemText(String pem) throws Exception {
        String cleaned =
                pem.replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] x509 = decodeBase64Flexible(cleaned);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(x509);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
