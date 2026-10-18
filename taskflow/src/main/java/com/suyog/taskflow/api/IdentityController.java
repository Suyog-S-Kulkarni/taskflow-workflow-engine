package com.suyog.taskflow.api;

import java.util.UUID;

import com.suyog.taskflow.security.CurrentTenant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class IdentityController {

    private final CurrentTenant currentTenant;

    public IdentityController(CurrentTenant currentTenant) {
        this.currentTenant = currentTenant;
    }

    @GetMapping
    public IdentityResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new IdentityResponse(
                jwt.getSubject(),
                currentTenant.requireTenantId()
        );
    }

    public record IdentityResponse(
            String subject,
            UUID tenantId
    ) {
    }
}