package com.suyog.taskflow.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowDefinitionControllerIntegrationTest {

    private static final String VALID_REQUEST = """
            {
              "name": "stored-workflow",
              "tasks": [
                {
                  "id": "payments",
                  "type": "MOCK_SUCCESS",
                  "dependsOn": []
                },
                {
                  "id": "report",
                  "type": "MOCK_SUCCESS",
                  "dependsOn": ["payments"]
                }
              ]
            }
            """;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();

        when(jwtDecoder.decode("writer-a")).thenReturn(
                token(
                        "writer-a",
                        tenantA,
                        "workflow-read",
                        "workflow-write"
                )
        );

        when(jwtDecoder.decode("reader-a")).thenReturn(
                token("reader-a", tenantA, "workflow-read")
        );

        when(jwtDecoder.decode("reader-b")).thenReturn(
                token("reader-b", tenantB, "workflow-read")
        );

        when(jwtDecoder.decode("no-roles")).thenReturn(
                token("no-roles", tenantA)
        );
    }

    @Test
    void createsAndReadsWorkflowWithinTenant() throws Exception {
        MvcResult created = createWorkflow();

        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);

        String workflowId = jsonMapper.readTree(
                created.getResponse().getContentAsString()
        ).get("workflowId").asText();

        mockMvc.perform(get(location)
                        .header("Authorization", "Bearer reader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value(workflowId))
                .andExpect(jsonPath("$.tenantId").value(tenantA.toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.name").value("stored-workflow"))
                .andExpect(
                        jsonPath("$.tasks[1].dependsOn[0]")
                                .value("payments")
                );
    }

    @Test
    void returnsNotFoundForAnotherTenantAndUnknownWorkflow()
            throws Exception {

        String location = createWorkflow()
                .getResponse()
                .getHeader("Location");

        assertNotNull(location);

        mockMvc.perform(get(location)
                        .header("Authorization", "Bearer reader-b"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(
                        "/api/workflows/" + UUID.randomUUID()
                                + "/versions/1"
                ).header("Authorization", "Bearer reader-b"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ignoresTenantHeaderDuringCreation() throws Exception {
        mockMvc.perform(post("/api/workflows")
                        .header("Authorization", "Bearer writer-a")
                        .header("X-Tenant-Id", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantA.toString()));

        assertEquals(1, countForTenant(tenantA));
        assertEquals(0, countForTenant(tenantB));
    }

    @Test
    void readOnlyUserCannotCreateWorkflow() throws Exception {
        mockMvc.perform(post("/api/workflows")
                        .header("Authorization", "Bearer reader-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());

        assertEquals(0, countForTenant(tenantA));
    }

    @Test
    void userWithoutRolesCannotReadWorkflow() throws Exception {
        String location = createWorkflow()
                .getResponse()
                .getHeader("Location");

        assertNotNull(location);

        mockMvc.perform(get(location)
                        .header("Authorization", "Bearer no-roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(
                        "/api/workflows/" + UUID.randomUUID()
                                + "/versions/1"
                ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidGraphDoesNotInsertWorkflow() throws Exception {
        String invalidRequest = """
                {
                  "name": "invalid-workflow",
                  "tasks": [
                    {
                      "id": "report",
                      "type": "MOCK_SUCCESS",
                      "dependsOn": ["missing"]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows")
                        .header("Authorization", "Bearer writer-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKFLOW"));

        assertEquals(0, countForTenant(tenantA));
    }

    @Test
    void rejectsInvalidVersionAndMalformedWorkflowId()
            throws Exception {

        mockMvc.perform(get(
                        "/api/workflows/" + UUID.randomUUID()
                                + "/versions/0"
                ).header("Authorization", "Bearer reader-a"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(
                        "/api/workflows/not-a-uuid/versions/1"
                ).header("Authorization", "Bearer reader-a"))
                .andExpect(status().isBadRequest());
    }

    private MvcResult createWorkflow() throws Exception {
        return mockMvc.perform(post("/api/workflows")
                        .header("Authorization", "Bearer writer-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private int countForTenant(UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workflow_definition
                WHERE tenant_id = ?
                """,
                Integer.class,
                tenantId.toString()
        );

        return count == null ? 0 : count;
    }

    private Jwt token(
            String tokenValue,
            UUID tenantId,
            String... roles
    ) {
        Instant now = Instant.now();

        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(tokenValue)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("aud", List.of("flowforge-api"))
                .claim("tenant_id", tenantId.toString())
                .claim(
                        "realm_access",
                        Map.of("roles", List.of(roles))
                )
                .build();
    }
}