package com.suyog.taskflow.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowVersion(
        UUID id,
        UUID tenantId,
        UUID workflowId,
        int version,
        WorkflowDefinition definition,
        Instant createdAt
) {

    public WorkflowVersion {
        Objects.requireNonNull(id, "Version ID must not be null");
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(workflowId, "Workflow ID must not be null");
        Objects.requireNonNull(definition, "Definition must not be null");
        Objects.requireNonNull(createdAt, "Creation time must not be null");

        if (version < 1) {
            throw new IllegalArgumentException(
                    "Definition version must be at least 1"
            );
        }

        if (definition.name().isBlank()
                || definition.name().length() > 100) {
            throw new IllegalArgumentException(
                    "Workflow name must contain between 1 and 100 characters"
            );
        }
    }
}