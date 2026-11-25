package com.zverev.auth.unit.security;

import com.zverev.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-key-for-jwt-signing-at-least-32-bytes-long";

    private static final String OTHER_SECRET =
            "another-test-secret-key-for-jwt-signing-at-least-32-bytes";

    private static final long EXPIRATION_MS = 60_000;

    private JwtService jwtService;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);

        userDetails = User.builder()
                .username("test-user")
                .password("encoded-password")
                .authorities("ROLE_ADMIN")
                .build();
    }

    @Test
    void shouldGenerateTokenWithCorrectClaims() {
        String token = jwtService.generateToken(userDetails);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertNotNull(token);
        assertEquals("test-user", claims.getSubject());
        assertEquals("ROLE_ADMIN", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertEquals(
                EXPIRATION_MS,
                claims.getExpiration().getTime() - claims.getIssuedAt().getTime()
        );
    }

    @Test
    void shouldExtractUsernameFromToken() {
        String token = jwtService.generateToken(userDetails);

        String username = jwtService.extractUsername(token);

        assertEquals("test-user", username);
    }

    @Test
    void shouldReturnTrueWhenTokenIsValid() {
        String token = jwtService.generateToken(userDetails);

        boolean result = jwtService.isTokenValid(token, userDetails);

        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenUsernameDoesNotMatch() {
        String token = jwtService.generateToken(userDetails);

        UserDetails anotherUser = User.builder()
                .username("another-user")
                .password("encoded-password")
                .authorities("ROLE_ADMIN")
                .build();

        boolean result = jwtService.isTokenValid(token, anotherUser);

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenTokenIsExpired() {
        JwtService expiredJwtService = new JwtService(SECRET, -1_000);

        String token = expiredJwtService.generateToken(userDetails);

        boolean result = jwtService.isTokenValid(token, userDetails);

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenTokenIsMalformed() {
        boolean result = jwtService.isTokenValid("invalid-token", userDetails);

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenTokenHasInvalidSignature() {
        JwtService anotherJwtService = new JwtService(OTHER_SECRET, EXPIRATION_MS);

        String token = anotherJwtService.generateToken(userDetails);

        boolean result = jwtService.isTokenValid(token, userDetails);

        assertFalse(result);
    }
}
