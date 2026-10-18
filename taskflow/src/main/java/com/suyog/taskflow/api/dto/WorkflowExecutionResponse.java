package com.suyog.taskflow.api.dto;

import com.suyog.taskflow.execution.domain.TaskStatus;

import java.util.Map;
import java.util.Objects;

public record WorkflowExecutionResponse(
        String workflowName,
        boolean succeeded,
        Map<String, TaskStatus> taskStatuses,
        Map<String, Integer> attemptCounts
) {

    public WorkflowExecutionResponse {
        Objects.requireNonNull(
                workflowName,
                "Workflow name must not be null"
        );

        taskStatuses = Map.copyOf(
                Objects.requireNonNull(
                        taskStatuses,
                        "Task statuses must not be null"
                )
        );

        attemptCounts = Map.copyOf(
                Objects.requireNonNull(
                        attemptCounts,
                        "Attempt counts must not be null"
                )
        );
    }
}