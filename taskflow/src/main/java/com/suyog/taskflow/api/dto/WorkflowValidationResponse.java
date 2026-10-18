package com.suyog.taskflow.api.dto;

import java.util.List;

public record WorkflowValidationResponse(
        boolean valid,
        String workflowName,
        int taskCount,
        List<String> executionOrder
) {

    public WorkflowValidationResponse {
        executionOrder = List.copyOf(executionOrder);
    }
}