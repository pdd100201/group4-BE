package com.onlinelearning.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

@Component
public class JwtTokenProvider {
    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-token-minutes}") long accessMinutes,
                            @Value("${app.jwt.refresh-token-days}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(sha256(secret));
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public String accessToken(String email, String sessionId,
                              Collection<? extends GrantedAuthority> authorities) {
        return token(email, sessionId, ACCESS, authorities, Duration.ofMinutes(accessMinutes));
    }

    public String refreshToken(String email, String sessionId) {
        return token(email, sessionId, REFRESH, List.of(), Duration.ofDays(refreshDays));
    }

    public Instant refreshExpiresAt() {
        return Instant.now().plus(Duration.ofDays(refreshDays));
    }

    public Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String subject(String token) { return claims(token).getSubject(); }
    public String sessionId(String token) { return claims(token).get("sid", String.class); }

    public boolean valid(String token, String expectedPurpose) {
        try {
            return expectedPurpose.equals(claims(token).get("purpose", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String token(String subject, String sessionId, String purpose,
                         Collection<? extends GrantedAuthority> roles, Duration ttl) {
        return Jwts.builder().subject(subject).id(UUID.randomUUID().toString())
                .claim("sid", sessionId).claim("purpose", purpose)
                .claim("roles", roles.stream().map(GrantedAuthority::getAuthority).toList())
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plus(ttl)))
                .signWith(key).compact();
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
