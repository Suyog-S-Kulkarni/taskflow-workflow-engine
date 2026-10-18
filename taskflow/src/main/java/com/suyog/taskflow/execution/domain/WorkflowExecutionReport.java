package com.suyog.taskflow.execution.domain;

import java.util.Map;
import java.util.Objects;

public record WorkflowExecutionReport(
        String workflowName,
        Map<String, TaskStatus> taskStatuses,
        Map<String, Integer> attemptCounts
) {

    public WorkflowExecutionReport {
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

    public boolean succeeded() {
        return !taskStatuses.isEmpty()
                && taskStatuses.values().stream()
                .allMatch(status -> status == TaskStatus.SUCCEEDED);
    }
}