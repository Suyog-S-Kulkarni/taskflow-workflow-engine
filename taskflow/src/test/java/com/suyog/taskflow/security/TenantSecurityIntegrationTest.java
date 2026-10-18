package com.suyog.taskflow.security;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class TenantSecurityIntegrationTest {

    private static final String TENANT_A =
            "11111111-1111-4111-8111-111111111111";

    private static final String TENANT_B =
            "22222222-2222-4222-8222-222222222222";

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(get("/api/me")
                        .header(
                                "Authorization",
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAuthenticatedTenantAndIgnoresTenantHeader()
            throws Exception {

        when(jwtDecoder.decode("tenant-a-token"))
                .thenReturn(token("tenant-a-token", "user-a", TENANT_A));

        mockMvc.perform(get("/api/me")
                        .header(
                                "Authorization",
                                "Bearer tenant-a-token"
                        )
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("user-a"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A));
    }

    @Test
    void returnsDifferentIdentityForTenantB() throws Exception {
        when(jwtDecoder.decode("tenant-b-token"))
                .thenReturn(token("tenant-b-token", "user-b", TENANT_B));

        mockMvc.perform(get("/api/me")
                        .header(
                                "Authorization",
                                "Bearer tenant-b-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("user-b"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_B));
    }

    private Jwt token(String value, String subject, String tenantId) {
        Instant now = Instant.now();

        return Jwt.withTokenValue(value)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("aud", List.of("flowforge-api"))
                .claim("tenant_id", tenantId)
                .build();
    }
}