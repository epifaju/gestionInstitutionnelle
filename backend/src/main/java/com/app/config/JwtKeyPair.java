package com.app.config;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record JwtKeyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey) {}

