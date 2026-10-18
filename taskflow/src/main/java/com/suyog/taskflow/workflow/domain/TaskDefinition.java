package com.suyog.taskflow.workflow.domain;

import java.util.List;
import java.util.Objects;

public record TaskDefinition(
        String id,
        String type,
        List<String> dependsOn
) {

    public TaskDefinition {
        Objects.requireNonNull(id, "Task ID must not be null");
        Objects.requireNonNull(type, "Task type must not be null");
        Objects.requireNonNull(dependsOn, "Dependencies must not be null");

        dependsOn = List.copyOf(dependsOn);
    }
}