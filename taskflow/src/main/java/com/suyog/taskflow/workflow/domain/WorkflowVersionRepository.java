package com.suyog.taskflow.workflow.domain;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowVersionRepository {

    void insert(WorkflowVersion version);

    Optional<WorkflowVersion> findVersion(
            UUID tenantId,
            UUID workflowId,
            int version
    );
}