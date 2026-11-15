package com.zverev.security;

import java.time.Instant;

public record JwtClaims(
        String sub,
        Instant iat,
        Instant exp
) {}
