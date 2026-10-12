package com.mspace.global.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Access Token 발급과 검증 (AU-03). 토큰 안에는 회원 id만 넣어요.
 * 학번·이름 같은 개인정보는 넣지 않아요. JWT 내용은 암호화가 아니라 누구나 읽을 수 있는 Base64예요.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        // 32바이트(256비트)보다 짧으면 jjwt가 서버 시작 시점에 예외를 던져요 → 약한 키로 운영되는 걸 막아요
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(Long userId) {
        var now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenValidity())))
                .signWith(key)
                .compact();
    }

    /** 로그인 응답의 expiresIn (초) */
    public long getValiditySeconds() {
        return properties.accessTokenValidity().toSeconds();
    }

    /** 서명이 맞고 만료되지 않았으면 회원 id, 아니면 빈 값 (위조, 만료, 형식 오류 모두) */
    public Optional<Long> parseUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
