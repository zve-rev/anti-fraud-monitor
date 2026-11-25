package com.zverev.auth.integration.controller;

import com.jayway.jsonpath.JsonPath;
import com.zverev.auth.security.JwtService;
import com.zverev.auth.security.StaffUserDetailsService;
import com.zverev.shared.entity.Staff;
import com.zverev.shared.entity.enums.StaffRole;
import com.zverev.shared.repository.StaffRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class AuthControllerIntegrationTest {

    private static final String JWT_SECRET =
            "integration-test-secret-key-must-be-at-least-32-bytes";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("antifraud_auth_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                postgres::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration");
        registry.add("security.jwt.secret", () -> JWT_SECRET);
        registry.add("security.jwt.expiration-ms", () -> 86400000L);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Staff staff;

    @Autowired
    private StaffUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        staffRepository.deleteAll();

        staff = Staff.builder()
                .login("integration-user")
                .password(passwordEncoder.encode("correct-password"))
                .role(StaffRole.STAFF)
                .enabled(true)
                .build();

        staffRepository.saveAndFlush(staff);
    }

    @Test
    void shouldAuthenticateStaffAndReturnValidJwtToken() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "integration-user",
                                    "password": "correct-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.token");

        assertEquals(3, token.split("\\.").length);
        assertEquals("integration-user", jwtService.extractUsername(token));

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(
                        JWT_SECRET.getBytes(StandardCharsets.UTF_8)
                ))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("integration-user", claims.getSubject());
        assertEquals("ROLE_STAFF", claims.get("role", String.class));
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    void shouldReturnUnauthorizedWhenPasswordIsIncorrect() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "integration-user",
                                    "password": "incorrect-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWhenStaffDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "missing-user",
                                    "password": "correct-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnBadRequestWhenLoginIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "",
                                    "password": "correct-password"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "integration-user",
                                    "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWhenStaffIsDisabled() throws Exception {
        staff.setEnabled(false);
        staffRepository.saveAndFlush(staff);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "integration-user",
                                    "password": "correct-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccessThroughSecurityWhenJwtIsValid() throws Exception {
        var userDetails = userDetailsService.loadUserByUsername("integration-user");
        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(get("/api/v1/protected-resource")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/protected-resource"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/protected-resource")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtIsExpired() throws Exception {
        var userDetails = userDetailsService.loadUserByUsername("integration-user");

        Date now = new Date();

        String expiredToken = Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", userDetails.getAuthorities().iterator().next().getAuthority())
                .issuedAt(new Date(now.getTime() - 120000))
                .expiration(new Date(now.getTime() - 60000))
                .signWith(Keys.hmacShaKeyFor(
                        JWT_SECRET.getBytes(StandardCharsets.UTF_8)
                ))
                .compact();

        mockMvc.perform(get("/api/v1/protected-resource")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
