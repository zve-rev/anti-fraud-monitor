
package com.zverev.gateway.integration.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zverev.auth.AuthApplication;
import com.zverev.gateway.GatewayApplication;
import com.zverev.shared.entity.Staff;
import com.zverev.shared.entity.enums.StaffRole;
import com.zverev.shared.repository.StaffRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
class GatewayAuthServiceE2ETest {

    private static final String JWT_SECRET =
            "integration-test-secret-key-must-be-at-least-32-bytes";

    private static final String LOGIN = "integration-user";

    private static final String PASSWORD = "correct-password";

    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("antifraud_e2e")
                    .withUsername("test")
                    .withPassword("test");

    private static ConfigurableApplicationContext authContext;

    private static ConfigurableApplicationContext gatewayContext;

    private static DisposableServer upstreamServer;

    private static WebTestClient gatewayClient;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        startUpstreamServer();
        startAuthService();
        createTestStaff();
        startGateway();
    }

    private static void startUpstreamServer() {
        upstreamServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    String login = request.requestHeaders()
                            .get("X-Staff-Login");

                    String role = request.requestHeaders()
                            .get("X-Staff-Role");

                    return response.status(200)
                            .sendString(Mono.just(login + "|" + role));
                })
                .bindNow();
    }

    private static void startAuthService() {
        authContext = new SpringApplicationBuilder(AuthApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--spring.config.location=classpath:/auth-e2e.yaml",
                        "--spring.autoconfigure.exclude=org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration,org.springframework.cloud.gateway.config.GatewayAutoConfiguration,org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration",
                        "--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--security.jwt.secret=" + JWT_SECRET,
                        "--spring.main.banner-mode=off"
                );
    }

    private static void createTestStaff() {
        StaffRepository staffRepository =
                authContext.getBean(StaffRepository.class);

        PasswordEncoder passwordEncoder =
                authContext.getBean(PasswordEncoder.class);

        Staff staff = Staff.builder()
                .login(LOGIN)
                .password(passwordEncoder.encode(PASSWORD))
                .role(StaffRole.STAFF)
                .enabled(true)
                .build();

        staffRepository.saveAndFlush(staff);
    }

    private static void startGateway() {
        int authPort = ((WebServerApplicationContext) authContext)
                .getWebServer()
                .getPort();

        int upstreamPort = upstreamServer.port();

        gatewayContext = new SpringApplicationBuilder(GatewayApplication.class)
                .web(WebApplicationType.REACTIVE)
                .run(
                        "--server.port=0",
                        "--management.server.port=0",
                        "--security.jwt.secret=" + JWT_SECRET,
                        "--spring.autoconfigure.exclude=org.springframework.boot.tomcat.autoconfigure.actuate.web.server.TomcatReactiveManagementContextAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.reactive.ReactiveManagementWebSecurityAutoConfiguration",

                        "--spring.cloud.gateway.server.webflux.routes[0].id=auth-service",
                        "--spring.cloud.gateway.server.webflux.routes[0].uri=http://127.0.0.1:" + authPort,
                        "--spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/v1/auth/**",

                        "--spring.cloud.gateway.server.webflux.routes[1].id=test-protected",
                        "--spring.cloud.gateway.server.webflux.routes[1].uri=http://127.0.0.1:" + upstreamPort,
                        "--spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/api/v1/protected/**",

                        "--spring.main.banner-mode=off"
                );

        int gatewayPort = ((WebServerApplicationContext) gatewayContext)
                .getWebServer()
                .getPort();

        gatewayClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + gatewayPort)
                .build();
    }

    private static String loginAndGetToken() throws Exception {
        EntityExchangeResult<byte[]> loginResult = gatewayClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "login": "integration-user",
                            "password": "correct-password"
                        }
                        """)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();

        String loginResponse = new String(
                loginResult.getResponseBody(),
                StandardCharsets.UTF_8
        );

        JsonNode jsonNode = OBJECT_MAPPER.readTree(loginResponse);

        String token = jsonNode.get("token").asText();

        assertNotNull(token);
        assertFalse(token.isBlank());

        return token;
    }

    private static String createTokenWithInvalidSignature() {
        SecretKey anotherKey = Keys.hmacShaKeyFor(
                "another-integration-secret-key-at-least-32-bytes"
                        .getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.builder()
                .subject(LOGIN)
                .claim("role", "ROLE_STAFF")
                .signWith(anotherKey)
                .compact();
    }

    private static String createExpiredToken() {
        return Jwts.builder()
                .subject(LOGIN)
                .claim("role", "ROLE_STAFF")
                .issuedAt(new Date(0))
                .expiration(new Date(1_000))
                .signWith(SIGNING_KEY)
                .compact();
    }

    @Test
    void shouldAuthenticateThroughGatewayAndForwardRealJwtClaims() throws Exception {
        String token = loginAndGetToken();

        EntityExchangeResult<byte[]> protectedResult = gatewayClient.get()
                .uri("/api/v1/protected/resource")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();

        String forwardedClaims = new String(
                protectedResult.getResponseBody(),
                StandardCharsets.UTF_8
        );

        assertEquals(
                "integration-user|ROLE_STAFF",
                forwardedClaims
        );
    }

    @Test
    void shouldRejectProtectedRequestWithoutJwt() {
        gatewayClient.get()
                .uri("/api/v1/protected/resource")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() {
        String invalidToken = createTokenWithInvalidSignature();

        gatewayClient.get()
                .uri("/api/v1/protected/resource")
                .headers(headers -> headers.setBearerAuth(invalidToken))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldRejectExpiredToken() {
        String expiredToken = createExpiredToken();

        gatewayClient.get()
                .uri("/api/v1/protected/resource")
                .headers(headers -> headers.setBearerAuth(expiredToken))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldRejectMalformedToken() {
        gatewayClient.get()
                .uri("/api/v1/protected/resource")
                .headers(headers -> headers.setBearerAuth("not-a-valid-jwt"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldRejectAuthorizationHeaderWithInvalidScheme() {
        gatewayClient.get()
                .uri("/api/v1/protected/resource")
                .headers(headers -> headers.set(
                        "Authorization",
                        "Basic dXNlcjpwYXNzd29yZA=="
                ))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldRejectLoginWithIncorrectPassword() {
        gatewayClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "login": "integration-user",
                            "password": "incorrect-password"
                        }
                        """)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldNotAllowClientToSpoofStaffHeaders() throws Exception {
        String token = loginAndGetToken();

        EntityExchangeResult<byte[]> protectedResult = gatewayClient.get()
                .uri("/api/v1/protected/resource")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.set("X-Staff-Login", "attacker");
                    headers.set("X-Staff-Role", "ROLE_ADMIN");
                })
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();

        String forwardedClaims = new String(
                protectedResult.getResponseBody(),
                StandardCharsets.UTF_8
        );

        assertEquals(
                "integration-user|ROLE_STAFF",
                forwardedClaims
        );
    }

    @AfterAll
    static void tearDown() {
        if (gatewayContext != null) {
            gatewayContext.close();
        }

        if (authContext != null) {
            authContext.close();
        }

        if (upstreamServer != null) {
            upstreamServer.disposeNow();
        }
    }
}
