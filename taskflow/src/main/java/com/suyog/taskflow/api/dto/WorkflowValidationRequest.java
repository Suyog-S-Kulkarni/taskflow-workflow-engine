package com.suyog.taskflow.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkflowValidationRequest(

        @NotBlank(message = "Workflow name must not be blank")
        @Size(max = 100, message = "Workflow name must not exceed 100 characters")
        String name,

        @NotEmpty(message = "Workflow must contain at least one task")
        @Size(max = 100, message = "Workflow must not contain more than 100 tasks")
        List<@NotNull @Valid TaskRequest> tasks

) {

    public record TaskRequest(

            @NotBlank(message = "Task ID must not be blank")
            @Size(max = 100, message = "Task ID must not exceed 100 characters")
            String id,

            @NotBlank(message = "Task type must not be blank")
            @Size(max = 100, message = "Task type must not exceed 100 characters")
            String type,

            @NotNull(message = "Dependencies must not be null")
            @Size(max = 100, message = "Too many dependencies for one task")
            List<
                    @NotBlank(message = "Dependency ID must not be blank")
                    @Size(
                            max = 100,
                            message = "Dependency ID must not exceed 100 characters"
                    )
                            String
                    > dependsOn

    ) {
    }
}