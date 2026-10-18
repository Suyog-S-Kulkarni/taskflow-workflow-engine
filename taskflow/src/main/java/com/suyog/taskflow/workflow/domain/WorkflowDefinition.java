package com.suyog.taskflow.workflow.domain;

import java.util.List;
import java.util.Objects;

public record WorkflowDefinition(
        String name,
        List<TaskDefinition> tasks
) {

    public WorkflowDefinition {
        Objects.requireNonNull(name, "Workflow name must not be null");
        Objects.requireNonNull(tasks, "Tasks must not be null");

        tasks = List.copyOf(tasks);
    }
}