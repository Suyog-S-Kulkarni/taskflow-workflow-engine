package com.suyog.taskflow.workflow.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class WorkflowValidator {

    private static final int MAX_TASKS = 100;
    private static final int MAX_DEPENDENCIES = 500;

    public List<String> validateAndGetExecutionOrder(
            WorkflowDefinition workflow
    ) {
        Objects.requireNonNull(workflow, "Workflow must not be null");

        if (workflow.name().isBlank()) {
            throw new WorkflowValidationException(
                    "Workflow name must not be blank"
            );
        }

        if (workflow.tasks().isEmpty()) {
            throw new WorkflowValidationException(
                    "Workflow must contain at least one task"
            );
        }

        if (workflow.tasks().size() > MAX_TASKS) {
            throw new WorkflowValidationException(
                    "Workflow must not contain more than "
                            + MAX_TASKS + " tasks"
            );
        }

        Map<String, TaskDefinition> tasksById = new LinkedHashMap<>();

        for (TaskDefinition task : workflow.tasks()) {
            if (task.id().isBlank()) {
                throw new WorkflowValidationException(
                        "Task ID must not be blank"
                );
            }

            if (task.type().isBlank()) {
                throw new WorkflowValidationException(
                        "Task type must not be blank for task: " + task.id()
                );
            }

            if (tasksById.putIfAbsent(task.id(), task) != null) {
                throw new WorkflowValidationException(
                        "Duplicate task ID: " + task.id()
                );
            }
        }

        Map<String, List<String>> dependants = new HashMap<>();
        Map<String, Integer> remainingDependencies = new HashMap<>();

        for (String taskId : tasksById.keySet()) {
            dependants.put(taskId, new ArrayList<>());
            remainingDependencies.put(taskId, 0);
        }

        int edgeCount = 0;

        for (TaskDefinition task : tasksById.values()) {
            Set<String> seenDependencies = new HashSet<>();

            for (String dependencyId : task.dependsOn()) {
                if (dependencyId.isBlank()) {
                    throw new WorkflowValidationException(
                            "Blank dependency for task: " + task.id()
                    );
                }

                if (dependencyId.equals(task.id())) {
                    throw new WorkflowValidationException(
                            "Task cannot depend on itself: " + task.id()
                    );
                }

                if (!seenDependencies.add(dependencyId)) {
                    throw new WorkflowValidationException(
                            "Duplicate dependency '" + dependencyId
                                    + "' for task: " + task.id()
                    );
                }

                if (!tasksById.containsKey(dependencyId)) {
                    throw new WorkflowValidationException(
                            "Unknown dependency '" + dependencyId
                                    + "' for task: " + task.id()
                    );
                }

                edgeCount++;

                if (edgeCount > MAX_DEPENDENCIES) {
                    throw new WorkflowValidationException(
                            "Workflow must not contain more than "
                                    + MAX_DEPENDENCIES + " dependencies"
                    );
                }

                dependants.get(dependencyId).add(task.id());

                remainingDependencies.put(
                        task.id(),
                        remainingDependencies.get(task.id()) + 1
                );
            }
        }

        Queue<String> readyTasks = new ArrayDeque<>();

        for (String taskId : tasksById.keySet()) {
            if (remainingDependencies.get(taskId) == 0) {
                readyTasks.add(taskId);
            }
        }

        List<String> executionOrder = new ArrayList<>();

        while (!readyTasks.isEmpty()) {
            String completedTaskId = readyTasks.remove();
            executionOrder.add(completedTaskId);

            for (String dependantId : dependants.get(completedTaskId)) {
                int remaining =
                        remainingDependencies.get(dependantId) - 1;

                remainingDependencies.put(dependantId, remaining);

                if (remaining == 0) {
                    readyTasks.add(dependantId);
                }
            }
        }

        if (executionOrder.size() != tasksById.size()) {
            throw new WorkflowValidationException(
                    "Workflow contains a circular dependency"
            );
        }

        return List.copyOf(executionOrder);
    }
}