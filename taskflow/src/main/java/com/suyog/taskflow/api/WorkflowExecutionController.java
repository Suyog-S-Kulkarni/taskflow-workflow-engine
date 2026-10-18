package com.suyog.taskflow.api;

import com.suyog.taskflow.api.dto.WorkflowExecutionResponse;
import com.suyog.taskflow.api.dto.WorkflowValidationRequest;
import com.suyog.taskflow.execution.application.WorkflowExecutionService;
import com.suyog.taskflow.execution.domain.WorkflowExecutionReport;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowExecutionController {

    private final WorkflowExecutionService service;

    public WorkflowExecutionController(
            WorkflowExecutionService service
    ) {
        this.service = service;
    }

    @PostMapping("/execute-preview")
    public WorkflowExecutionResponse executePreview(
            @Valid @RequestBody WorkflowValidationRequest request
    ) throws InterruptedException {

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

        WorkflowExecutionReport report =
                service.executePreview(workflow);

        return new WorkflowExecutionResponse(
                report.workflowName(),
                report.succeeded(),
                report.taskStatuses(),
                report.attemptCounts()
        );
    }
}