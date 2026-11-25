package com.zverev.auth.unit.security;

import com.zverev.auth.controller.AuthController;
import com.zverev.auth.security.JwtAuthenticationFilter;
import com.zverev.auth.security.JwtService;
import com.zverev.auth.security.SecurityConfiguration;
import com.zverev.auth.security.StaffUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class
})
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private StaffUserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldAllowAccessToLoginWithoutAuthentication() throws Exception {
        UserDetails userDetails = User.builder()
                .username("test-user")
                .password("encoded-password")
                .authorities("ROLE_STAFF")
                .build();

        when(userDetailsService.loadUserByUsername("test-user"))
                .thenReturn(userDetails);

        when(jwtService.generateToken(userDetails))
                .thenReturn("generated-jwt-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "test-user",
                                    "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("generated-jwt-token"));

        verify(authenticationManager).authenticate(any());
        verify(userDetailsService).loadUserByUsername("test-user");
        verify(jwtService).generateToken(userDetails);
    }

    @Test
    void shouldRequireAuthenticationForProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccessToHealthEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowAccessToNestedHealthEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowAccessToInfoEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowAccessToPrometheusEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotCreateHttpSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andReturn();

        assertNull(result.getRequest().getSession(false));
    }
}
