package com.suyog.taskflow.workflow.persistence;

import com.suyog.taskflow.workflow.domain.TaskDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;

@Component
public class WorkflowTaskJsonCodec {

    private static final TypeReference<List<TaskDefinition>> TASK_LIST_TYPE =
            new TypeReference<>() {
            };

    private final JsonMapper jsonMapper;

    public WorkflowTaskJsonCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(List<TaskDefinition> tasks) {
        Objects.requireNonNull(tasks, "Tasks must not be null");

        return jsonMapper.writeValueAsString(tasks);
    }

    public List<TaskDefinition> decode(String json) {
        Objects.requireNonNull(json, "Stored task JSON must not be null");

        List<TaskDefinition> tasks = jsonMapper.readValue(
                json,
                TASK_LIST_TYPE
        );

        if (tasks == null) {
            throw new IllegalStateException(
                    "Stored task JSON must contain a task array"
            );
        }

        return List.copyOf(tasks);
    }
}