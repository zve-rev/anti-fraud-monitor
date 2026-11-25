package com.zverev.auth.unit.exception;

import com.zverev.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler =
            new GlobalExceptionHandler();

    @Test
    void shouldReturnUnauthorizedProblemDetail() {
        BadCredentialsException exception =
                new BadCredentialsException("Bad credentials");

        ProblemDetail result =
                exceptionHandler.handleAuthentication(exception);

        assertNotNull(result);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatus());
        assertEquals("Unauthorized", result.getTitle());
        assertEquals("Invalid login or password", result.getDetail());
    }
}
