package com.suyog.taskflow.api;

import com.suyog.taskflow.workflow.domain.WorkflowValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkflowValidationControllerTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean beanValidator;

    @BeforeEach
    void setUp() {
        beanValidator = new LocalValidatorFactoryBean();
        beanValidator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new WorkflowValidationController(
                                new WorkflowValidator()
                        )
                )
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(beanValidator)
                .build();
    }

    @AfterEach
    void tearDown() {
        beanValidator.close();
    }

    @Test
    void returnsExecutionOrderForValidWorkflow() throws Exception {
        String body = """
                {
                  "name": "daily-report",
                  "tasks": [
                    {
                      "id": "payments",
                      "type": "FETCH_PAYMENTS",
                      "dependsOn": []
                    },
                    {
                      "id": "report",
                      "type": "GENERATE_REPORT",
                      "dependsOn": ["payments"]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.workflowName").value("daily-report"))
                .andExpect(jsonPath("$.taskCount").value(2))
                .andExpect(jsonPath("$.executionOrder[0]").value("payments"))
                .andExpect(jsonPath("$.executionOrder[1]").value("report"));
    }

    @Test
    void returnsBadRequestForMissingDependency() throws Exception {
        String body = """
                {
                  "name": "daily-report",
                  "tasks": [
                    {
                      "id": "report",
                      "type": "GENERATE_REPORT",
                      "dependsOn": ["missing"]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKFLOW"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.message").value(
                        "Unknown dependency 'missing' for task: report"
                ));
    }

    @Test
    void returnsBadRequestForCircularDependency() throws Exception {
        String body = """
                {
                  "name": "cyclic-workflow",
                  "tasks": [
                    {
                      "id": "a",
                      "type": "MOCK",
                      "dependsOn": ["b"]
                    },
                    {
                      "id": "b",
                      "type": "MOCK",
                      "dependsOn": ["a"]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKFLOW"))
                .andExpect(jsonPath("$.message").value(
                        "Workflow contains a circular dependency"
                ));
    }

    @Test
    void validatesNestedTaskFieldsBeforeDomainMapping() throws Exception {
        String body = """
                {
                  "name": "daily-report",
                  "tasks": [
                    {
                      "id": "payments",
                      "type": "FETCH_PAYMENTS",
                      "dependsOn": null
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.violations[0].field").value(
                        "tasks[0].dependsOn"
                ));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}