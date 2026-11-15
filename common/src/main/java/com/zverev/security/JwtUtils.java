package com.zverev.security;

import com.zverev.exception.InvalidJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class JwtUtils {

    private JwtUtils() {}

    public static SecretKey keyFrom(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 bytes for HS256, got " + bytes.length);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public static String issue(String subject, long ttlSeconds, SecretKey key) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public static JwtClaims parse(String token, SecretKey key) {
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String sub = payload.getSubject();
            Date issuedAt = payload.getIssuedAt();
            Date expiration = payload.getExpiration();

            if (sub == null || issuedAt == null || expiration == null) {
                throw new InvalidJwtException("JWT missing required claims: sub, iat, exp");
            }

            return new JwtClaims(sub, issuedAt.toInstant(), expiration.toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidJwtException("Invalid JWT: " + e.getMessage(), e);
        }
    }
}