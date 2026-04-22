package com.app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtKeyPair jwtKeyPair(JwtProperties properties) throws Exception {
        RSAPrivateKey priv = PemKeys.readPrivateKeyFromPemBase64(properties.getPrivateKeyB64());
        RSAPublicKey pub = PemKeys.readPublicKeyFromPemBase64(properties.getPublicKeyB64());

        if (priv instanceof RSAPrivateCrtKey crt) {
            if (!crt.getModulus().equals(pub.getModulus())) {
                throw new IllegalArgumentException(
                        "JWT keys mismatch: JWT_PUBLIC_KEY_B64 ne correspond pas à JWT_PRIVATE_KEY_B64 (modulus différent).");
            }
        }

        return new JwtKeyPair(priv, pub);
    }

    @Bean
    public RSAPrivateKey jwtPrivateKey(JwtKeyPair keyPair) {
        return keyPair.privateKey();
    }

    @Bean
    public RSAPublicKey jwtPublicKey(JwtKeyPair keyPair) {
        return keyPair.publicKey();
    }
}
