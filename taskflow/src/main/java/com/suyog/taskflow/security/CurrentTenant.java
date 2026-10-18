package com.suyog.taskflow.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenant {

    public UUID requireTenantId() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !jwtAuth.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authenticated tenant identity is required"
            );
        }

        Object tenantClaim = jwtAuth.getToken()
                .getClaims()
                .get("tenant_id");

        if (!(tenantClaim instanceof String tenantId)) {
            throw new AccessDeniedException(
                    "Authenticated tenant identity is missing"
            );
        }

        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "Authenticated tenant identity is invalid"
            );
        }
    }
}