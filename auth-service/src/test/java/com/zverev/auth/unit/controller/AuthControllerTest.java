package com.zverev.auth.unit.controller;

import com.zverev.auth.controller.AuthController;
import com.zverev.auth.exception.GlobalExceptionHandler;
import com.zverev.auth.security.JwtService;
import com.zverev.auth.security.StaffUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private StaffUserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "login": "test-user",
                                    "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail")
                        .value("Invalid login or password"))
                .andExpect(jsonPath("$.status").value(401));

        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void shouldReturnBadRequestWhenLoginIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "login": "",
                                "password": "valid-password"
                            }
                            """))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "login": "test-user",
                                "password": ""
                            }
                            """))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void shouldReturnTokenWhenCredentialsAreValid() throws Exception {
        UserDetails user = User.withUsername("test-user")
                .password("encoded-password")
                .authorities("ROLE_USER")
                .build();

        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        user,
                        null,
                        user.getAuthorities()
                ));

        when(userDetailsService.loadUserByUsername("test-user"))
                .thenReturn(user);

        when(jwtService.generateToken(user))
                .thenReturn("test-jwt-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "login": "test-user",
                                "password": "correct-password"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"));

        verify(authenticationManager).authenticate(any());
        verify(userDetailsService).loadUserByUsername("test-user");
        verify(jwtService).generateToken(user);
    }
}
