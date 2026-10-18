package com.suyog.taskflow.security;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantTokenValidatorTest {

    private static final String TENANT_A =
            "11111111-1111-4111-8111-111111111111";

    private final TenantTokenValidator validator =
            new TenantTokenValidator("flowforge-api");

    @Test
    void acceptsValidTenantToken() {
        Jwt token = validToken().build();

        assertFalse(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsMissingTenant() {
        Jwt token = validToken()
                .claims(claims -> claims.remove("tenant_id"))
                .build();

        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsMalformedTenant() {
        Jwt token = validToken()
                .claim("tenant_id", "1-1-1-1-1")
                .build();

        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsMultipleTenantValues() {
        Jwt token = validToken()
                .claim("tenant_id", List.of(TENANT_A))
                .build();

        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsWrongAudience() {
        Jwt token = validToken()
                .claim("aud", List.of("another-api"))
                .build();

        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsMissingSubject() {
        Jwt token = validToken()
                .claims(claims -> claims.remove("sub"))
                .build();

        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsMissingExpiration() {
        Jwt token = validToken()
                .claims(claims -> claims.remove("exp"))
                .build();

        assertTrue(validator.validate(token).hasErrors());
    }

    private Jwt.Builder validToken() {
        Instant now = Instant.now();

        return Jwt.withTokenValue("unit-test-token")
                .header("alg", "RS256")
                .subject("user-a")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("aud", List.of("flowforge-api"))
                .claim("tenant_id", TENANT_A);
    }
}