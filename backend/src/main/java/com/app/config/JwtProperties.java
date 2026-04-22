package com.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String privateKeyB64;
    private String publicKeyB64;
    private int accessTokenMinutes = 15;
    private int refreshTokenDays = 7;
}
