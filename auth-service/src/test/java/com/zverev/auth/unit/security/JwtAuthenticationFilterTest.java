package com.zverev.auth.unit.security;

import com.zverev.auth.security.JwtAuthenticationFilter;
import com.zverev.auth.security.JwtService;
import com.zverev.auth.security.StaffUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.mock.web.MockFilterChain;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private StaffUserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        userDetails = User.builder()
                .username("test-user")
                .password("encoded-password")
                .authorities("ROLE_STAFF")
                .build();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNotNull(filterChain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderDoesNotStartWithBearer()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic some-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNotNull(filterChain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void shouldContinueFilterChainWhenTokenExtractionFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtService.extractUsername("invalid-token"))
                .thenThrow(new IllegalArgumentException("Invalid token"));

        filter.doFilter(request, response, filterChain);

        assertNotNull(filterChain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(jwtService).extractUsername("invalid-token");
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtService.extractUsername("valid-token"))
                .thenReturn("test-user");

        when(userDetailsService.loadUserByUsername("test-user"))
                .thenReturn(userDetails);

        when(jwtService.isTokenValid("valid-token", userDetails))
                .thenReturn(true);

        filter.doFilter(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals("test-user", authentication.getName());
        assertEquals(userDetails, authentication.getPrincipal());
        assertNull(authentication.getCredentials());
        assertEquals(
                "ROLE_STAFF",
                authentication.getAuthorities().iterator().next().getAuthority()
        );

        assertNotNull(filterChain.getRequest());

        verify(jwtService).extractUsername("valid-token");
        verify(userDetailsService).loadUserByUsername("test-user");
        verify(jwtService).isTokenValid("valid-token", userDetails);
    }

    @Test
    void shouldNotSetAuthenticationWhenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtService.extractUsername("invalid-token"))
                .thenReturn("test-user");

        when(userDetailsService.loadUserByUsername("test-user"))
                .thenReturn(userDetails);

        when(jwtService.isTokenValid("invalid-token", userDetails))
                .thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());

        verify(jwtService).extractUsername("invalid-token");
        verify(userDetailsService).loadUserByUsername("test-user");
        verify(jwtService).isTokenValid("invalid-token", userDetails);
    }

    @Test
    void shouldNotAuthenticateWhenAuthenticationAlreadyExists() throws Exception {
        var existingAuthentication =
                new UsernamePasswordAuthenticationToken(
                        "existing-user",
                        null,
                        userDetails.getAuthorities()
                );

        SecurityContextHolder.getContext().setAuthentication(existingAuthentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertSame(
                existingAuthentication,
                SecurityContextHolder.getContext().getAuthentication()
        );

        assertNotNull(filterChain.getRequest());

        verify(jwtService).extractUsername("valid-token");
        verifyNoInteractions(userDetailsService);
    }
}
