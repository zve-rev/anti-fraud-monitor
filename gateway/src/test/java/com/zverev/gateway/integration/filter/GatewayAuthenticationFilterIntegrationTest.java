package com.zverev.gateway.integration.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=integration-test-secret-key-with-at-least-32-bytes"
        }
)
@AutoConfigureWebTestClient
class GatewayAuthenticationFilterIntegrationTest {

    private static final String SECRET =
            "integration-test-secret-key-with-at-least-32-bytes";

    private static final String WRONG_SECRET =
            "wrong-integration-secret-key-with-at-least-32-bytes";

    private static final long TOKEN_VALIDITY_MS = 60_000;

    private static DisposableServer upstreamServer;

    @Autowired
    private WebTestClient webTestClient;

    private SecretKey signingKey;

    @DynamicPropertySource
    static void registerGatewayProperties(DynamicPropertyRegistry registry) {
        upstreamServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    String login = String.valueOf(
                            request.requestHeaders().get("X-Staff-Login")
                    );

                    String role = String.valueOf(
                            request.requestHeaders().get("X-Staff-Role")
                    );

                    return response
                            .status(200)
                            .sendString(Mono.just(login + "|" + role));
                })
                .bindNow();

        String upstreamUri = "http://127.0.0.1:" + upstreamServer.port();

        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].id",
                () -> "test-auth"
        );

        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> upstreamUri
        );

        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/api/v1/auth/**"
        );

        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].id",
                () -> "test-protected"
        );

        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].uri",
                () -> upstreamUri
        );

        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].predicates[0]",
                () -> "Path=/api/v1/protected/**"
        );
    }

    @AfterAll
    static void stopUpstreamServer() {
        if (upstreamServer != null) {
            upstreamServer.disposeNow();
        }
    }

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    void shouldAllowLoginWithoutJwt() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertEquals("null|null", body));
    }

    @Test
    void shouldForwardStaffClaimsFromValidJwt() {
        String token = createValidToken();

        webTestClient.get()
                .uri("/api/v1/protected/resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertEquals(
                        "integration-user|ROLE_STAFF",
                        body
                ));
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtIsMissing() {
        webTestClient.get()
                .uri("/api/v1/protected/resource")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthorizationHeaderIsInvalid() {
        webTestClient.get()
                .uri("/api/v1/protected/resource")
                .header(HttpHeaders.AUTHORIZATION, "Invalid token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtSignatureIsInvalid() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                WRONG_SECRET.getBytes(StandardCharsets.UTF_8)
        );

        String token = Jwts.builder()
                .subject("integration-user")
                .claim("role", "ROLE_STAFF")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS))
                .signWith(wrongKey)
                .compact();

        webTestClient.get()
                .uri("/api/v1/protected/resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtIsExpired() {
        String token = Jwts.builder()
                .subject("integration-user")
                .claim("role", "ROLE_STAFF")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(signingKey)
                .compact();

        webTestClient.get()
                .uri("/api/v1/protected/resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowOptionsRequestWithoutJwt() {
        webTestClient.method(HttpMethod.OPTIONS)
                .uri("/api/v1/protected/resource")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertEquals("null|null", body));
    }

    @Test
    void shouldAllowSwaggerApiDocsWithoutJwt() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(
                        org.springframework.http.MediaType.APPLICATION_JSON
                )
                .expectBody()
                .jsonPath("$.openapi").isEqualTo("3.1.0");
    }

    private String createValidToken() {
        return Jwts.builder()
                .subject("integration-user")
                .claim("role", "ROLE_STAFF")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS))
                .signWith(signingKey)
                .compact();
    }
}
