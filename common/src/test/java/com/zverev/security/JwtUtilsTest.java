package com.zverev.security;

import com.zverev.exception.InvalidJwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private final SecretKey key = JwtUtils.keyFrom("test-secret-that-is-at-least-32-bytes-long!!");
    private final SecretKey otherKey = JwtUtils.keyFrom("other-secret-that-is-at-least-32-bytes-long!!");

    @Test
    @DisplayName("выпустили и разобрали - данные совпали")
    void roundTrip() {
        String token = JwtUtils.issue("analyst-1", 900, key);

        JwtClaims claims = JwtUtils.parse(token, key);

        assertThat(claims.sub()).isEqualTo("analyst-1");
        assertThat(claims.exp()).isAfter(claims.iat());
    }

    @Test
    @DisplayName("чужой ключ - подпись не сходится")
    void wrongKey() {
        String token = JwtUtils.issue("analyst-1", 900, key);

        assertThatThrownBy(() -> JwtUtils.parse(token, otherKey))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    @DisplayName("подменили payload - подпись не сходится")
    void tamperedPayload() {
        String token = JwtUtils.issue("analyst-1", 900, key);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".dGFtcGVyZWQ." + parts[2];

        assertThatThrownBy(() -> JwtUtils.parse(tampered, key))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    @DisplayName("просроченный токен - отказ")
    void expiredToken() {
        String token = JwtUtils.issue("analyst-1", -10, key);

        assertThatThrownBy(() -> JwtUtils.parse(token, key))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("токен без exp - отказ, а не NPE")
    void tokenWithoutExpiration() {
        String token = Jwts.builder()
                .subject("analyst-1")
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> JwtUtils.parse(token, key))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("missing required claims");
    }

    @Test
    @DisplayName("короткий секрет - падаем сразу с понятной ошибкой")
    void shortSecret() {
        assertThatThrownBy(() -> JwtUtils.keyFrom("short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
