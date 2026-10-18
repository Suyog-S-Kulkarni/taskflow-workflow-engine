package com.suyog.taskflow.security;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class TenantTokenValidator
        implements OAuth2TokenValidator<Jwt> {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                    + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final String requiredAudience;

    public TenantTokenValidator(String requiredAudience) {
        this.requiredAudience = Objects.requireNonNull(
                requiredAudience,
                "Required audience must not be null"
        );

        if (requiredAudience.isBlank()) {
            throw new IllegalArgumentException(
                    "Required audience must not be blank"
            );
        }
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object audienceClaim = token.getClaims().get("aud");

        if (!hasRequiredAudience(audienceClaim)) {
            return invalid(
                    "Token does not contain the required audience"
            );
        }

        Object subjectClaim = token.getClaims().get("sub");

        if (!(subjectClaim instanceof String subjectValue)
                || subjectValue.isBlank()) {
            return invalid(
                    "Token must contain a user subject"
            );
        }

        if (token.getExpiresAt() == null) {
            return invalid(
                    "Token must contain an expiration time"
            );
        }

        Object tenantClaim = token.getClaims().get("tenant_id");

        if (!(tenantClaim instanceof String tenantValue)
                || !UUID_PATTERN.matcher(tenantValue).matches()) {
            return invalid(
                    "Token must contain a valid tenant_id"
            );
        }

        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasRequiredAudience(Object audienceClaim) {
        if (audienceClaim instanceof String audienceValue) {
            return requiredAudience.equals(audienceValue);
        }

        if (audienceClaim instanceof Collection<?> audienceValues) {
            return audienceValues.contains(requiredAudience);
        }

        return false;
    }

    private static OAuth2TokenValidatorResult invalid(
            String description) {

        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                description,
                null
        );

        return OAuth2TokenValidatorResult.failure(error);
    }
}