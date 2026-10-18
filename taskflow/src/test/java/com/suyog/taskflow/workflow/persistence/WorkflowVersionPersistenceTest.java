package com.suyog.taskflow.workflow.persistence;

import com.suyog.taskflow.workflow.application.WorkflowDefinitionService;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowValidationException;
import com.suyog.taskflow.workflow.domain.WorkflowVersion;
import com.suyog.taskflow.workflow.domain.WorkflowVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowVersionPersistenceTest {

    @Autowired
    private WorkflowDefinitionService service;

    @Autowired
    private WorkflowVersionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesAndLoadsWorkflowIncludingTaskDependencies() {
        UUID tenantId = UUID.randomUUID();

        WorkflowDefinition definition = definition(
                "daily-report",
                task("payments"),
                task("settlements"),
                task("report", "payments", "settlements")
        );

        WorkflowVersion created = service.create(
                tenantId,
                definition
        );

        WorkflowVersion loaded = service.findVersion(
                tenantId,
                created.workflowId(),
                1
        ).orElseThrow();

        assertEquals(created.id(), loaded.id());
        assertEquals(tenantId, loaded.tenantId());
        assertEquals(created.workflowId(), loaded.workflowId());
        assertEquals(1, loaded.version());
        assertEquals(definition, loaded.definition());
        assertEquals(created.createdAt(), loaded.createdAt());
    }

    @Test
    void doesNotReturnAnotherTenantsWorkflow() {
        UUID ownerTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();

        WorkflowVersion created = service.create(
                ownerTenant,
                definition("private-workflow", task("payments"))
        );

        assertTrue(
                service.findVersion(
                        otherTenant,
                        created.workflowId(),
                        1
                ).isEmpty()
        );

        assertTrue(
                service.findVersion(
                        ownerTenant,
                        created.workflowId(),
                        1
                ).isPresent()
        );
    }

    @Test
    void rejectsDuplicateVersionWithoutOverwritingOriginal() {
        UUID tenantId = UUID.randomUUID();

        WorkflowVersion original = service.create(
                tenantId,
                definition("original", task("payments"))
        );

        WorkflowVersion conflictingVersion = new WorkflowVersion(
                UUID.randomUUID(),
                tenantId,
                original.workflowId(),
                1,
                definition("changed", task("different-task")),
                now()
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.insert(conflictingVersion)
        );

        WorkflowVersion loaded = repository.findVersion(
                tenantId,
                original.workflowId(),
                1
        ).orElseThrow();

        assertEquals(original.id(), loaded.id());
        assertEquals("original", loaded.definition().name());

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workflow_definition
                WHERE tenant_id = ?
                  AND workflow_id = ?
                  AND definition_version = ?
                """,
                Integer.class,
                tenantId.toString(),
                original.workflowId().toString(),
                1
        );

        assertEquals(1, count.intValue());
    }

    @Test
    void storesDifferentVersionsWithoutChangingEarlierVersion() {
        UUID tenantId = UUID.randomUUID();

        WorkflowVersion first = service.create(
                tenantId,
                definition("version-one", task("payments"))
        );

        WorkflowVersion second = new WorkflowVersion(
                UUID.randomUUID(),
                tenantId,
                first.workflowId(),
                2,
                definition(
                        "version-two",
                        task("payments"),
                        task("report", "payments")
                ),
                now()
        );

        repository.insert(second);

        WorkflowVersion loadedFirst = repository.findVersion(
                tenantId,
                first.workflowId(),
                1
        ).orElseThrow();

        WorkflowVersion loadedSecond = repository.findVersion(
                tenantId,
                first.workflowId(),
                2
        ).orElseThrow();

        assertEquals(first.definition(), loadedFirst.definition());
        assertEquals(second.definition(), loadedSecond.definition());

        assertNotEquals(loadedFirst.id(), loadedSecond.id());

        assertEquals(
                loadedFirst.workflowId(),
                loadedSecond.workflowId()
        );
    }

    @Test
    void rejectsInvalidGraphBeforeInsertingAnything() {
        UUID tenantId = UUID.randomUUID();

        WorkflowDefinition invalid = definition(
                "invalid",
                task("report", "missing")
        );

        assertThrows(
                WorkflowValidationException.class,
                () -> service.create(tenantId, invalid)
        );

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workflow_definition
                WHERE tenant_id = ?
                """,
                Integer.class,
                tenantId.toString()
        );

        assertEquals(0, count.intValue());
    }

    @Test
    void returnsEmptyForMissingVersion() {
        UUID tenantId = UUID.randomUUID();

        WorkflowVersion created = service.create(
                tenantId,
                definition("example", task("payments"))
        );

        assertTrue(
                service.findVersion(
                        tenantId,
                        created.workflowId(),
                        99
                ).isEmpty()
        );
    }

    private static TaskDefinition task(
            String id,
            String... dependencies
    ) {
        return new TaskDefinition(
                id,
                "MOCK_SUCCESS",
                List.of(dependencies)
        );
    }

    private static WorkflowDefinition definition(
            String name,
            TaskDefinition... tasks
    ) {
        return new WorkflowDefinition(
                name,
                List.of(tasks)
        );
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}