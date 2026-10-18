package com.suyog.taskflow.api;

import com.suyog.taskflow.api.dto.WorkflowValidationRequest;
import com.suyog.taskflow.api.dto.WorkflowValidationResponse;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowValidator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowValidationController {

    private final WorkflowValidator validator;

    public WorkflowValidationController(WorkflowValidator validator) {
        this.validator = validator;
    }

    @PostMapping("/validate")
    public WorkflowValidationResponse validate(
            @Valid @RequestBody WorkflowValidationRequest request
    ) {
        List<TaskDefinition> tasks = request.tasks().stream()
                .map(task -> new TaskDefinition(
                        task.id(),
                        task.type(),
                        task.dependsOn()
                ))
                .toList();

        WorkflowDefinition workflow = new WorkflowDefinition(
                request.name(),
                tasks
        );

        List<String> executionOrder =
                validator.validateAndGetExecutionOrder(workflow);

        return new WorkflowValidationResponse(
                true,
                workflow.name(),
                workflow.tasks().size(),
                executionOrder
        );
    }
}