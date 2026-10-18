package com.suyog.taskflow.api;

import com.suyog.taskflow.api.dto.WorkflowValidationRequest;
import com.suyog.taskflow.security.CurrentTenant;
import com.suyog.taskflow.workflow.application.WorkflowDefinitionService;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowVersion;

import jakarta.validation.Valid;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService service;
    private final CurrentTenant currentTenant;

    public WorkflowDefinitionController(
            WorkflowDefinitionService service,
            CurrentTenant currentTenant
    ) {
        this.service = service;
        this.currentTenant = currentTenant;
    }

    @PostMapping
    public ResponseEntity<WorkflowVersionResponse> create(
            @Valid @RequestBody WorkflowValidationRequest request
    ) {
        UUID tenantId = currentTenant.requireTenantId();

        List<TaskDefinition> tasks = request.tasks().stream()
                .map(task -> new TaskDefinition(
                        task.id(),
                        task.type(),
                        task.dependsOn()
                ))
                .toList();

        WorkflowDefinition definition = new WorkflowDefinition(
                request.name(),
                tasks
        );

        WorkflowVersion created = service.create(
                tenantId,
                definition
        );

        URI location = URI.create(
                "/api/workflows/" + created.workflowId()
                        + "/versions/" + created.version()
        );

        return ResponseEntity
                .created(location)
                .body(toResponse(created));
    }

    @GetMapping("/{workflowId}/versions/{version}")
    public WorkflowVersionResponse findVersion(
            @PathVariable("workflowId") UUID workflowId,
            @PathVariable("version") int version
    ) {
        if (version < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Version must be at least 1"
            );
        }

        UUID tenantId = currentTenant.requireTenantId();

        WorkflowVersion stored = service.findVersion(
                tenantId,
                workflowId,
                version
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Workflow version not found"
        ));

        return toResponse(stored);
    }

    private WorkflowVersionResponse toResponse(
            WorkflowVersion stored
    ) {
        return new WorkflowVersionResponse(
                stored.id(),
                stored.workflowId(),
                stored.tenantId(),
                stored.version(),
                stored.definition().name(),
                stored.definition().tasks(),
                stored.createdAt()
        );
    }

    public record WorkflowVersionResponse(
            UUID versionId,
            UUID workflowId,
            UUID tenantId,
            int version,
            String name,
            List<TaskDefinition> tasks,
            Instant createdAt
    ) {
        public WorkflowVersionResponse {
            tasks = List.copyOf(tasks);
        }
    }
}