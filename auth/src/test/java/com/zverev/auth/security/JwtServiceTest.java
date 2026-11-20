package com.zverev.auth.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";
    private static final String OTHER_SECRET = "other-secret-that-is-at-least-32-bytes-long!!";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    @DisplayName("generated token - username can be extracted")
    void generateAndExtract() {
        String token = jwtService.generateToken(user("staff"));

        assertThat(jwtService.extractUsername(token)).isEqualTo("staff");
    }

    @Test
    @DisplayName("generated token - contains role claim")
    void containsRole() {
        String token = jwtService.generateToken(user("staff"));

        Object role = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role");

        assertThat(role).isEqualTo("ROLE_STAFF");
    }

    @Test
    @DisplayName("valid token for same user - valid")
    void validForSameUser() {
        UserDetails user = user("staff");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("valid token for another user - invalid")
    void invalidForOtherUser() {
        String token = jwtService.generateToken(user("staff"));

        assertThat(jwtService.isTokenValid(token, user("staff2"))).isFalse();
    }

    @Test
    @DisplayName("expired token - invalid, extraction fails")
    void expiredToken() {
        JwtService expiringService = new JwtService(SECRET, -1_000);
        UserDetails user = user("staff");
        String token = expiringService.generateToken(user);

        assertThat(expiringService.isTokenValid(token, user)).isFalse();
        assertThatThrownBy(() -> expiringService.extractUsername(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token signed with another secret - extraction fails")
    void wrongSignature() {
        String token = new JwtService(OTHER_SECRET, 60_000).generateToken(user("staff"));

        assertThatThrownBy(() -> jwtService.extractUsername(token)).isInstanceOf(JwtException.class);
    }

    private UserDetails user(String login) {
        return User.withUsername(login).password("x").roles("STAFF").build();
    }
}
