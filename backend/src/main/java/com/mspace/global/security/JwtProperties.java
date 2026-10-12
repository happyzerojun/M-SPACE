package com.mspace.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yaml의 mspace.jwt.* 설정 (secret, access-token-validity: 2h) */
@ConfigurationProperties(prefix = "mspace.jwt")
public record JwtProperties(String secret, Duration accessTokenValidity) {
}
