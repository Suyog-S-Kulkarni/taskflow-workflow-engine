package com.suyog.taskflow.workflow.persistence;

import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowVersion;
import com.suyog.taskflow.workflow.domain.WorkflowVersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcWorkflowVersionRepository
        implements WorkflowVersionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowTaskJsonCodec jsonCodec;

    public JdbcWorkflowVersionRepository(
            JdbcTemplate jdbcTemplate,
            WorkflowTaskJsonCodec jsonCodec
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public void insert(WorkflowVersion version) {
        Objects.requireNonNull(version, "Workflow version must not be null");

        String tasksJson = jsonCodec.encode(
                version.definition().tasks()
        );

        int affectedRows = jdbcTemplate.update(
                """
                INSERT INTO workflow_definition (
                    id,
                    tenant_id,
                    workflow_id,
                    definition_version,
                    name,
                    tasks_json,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                version.id().toString(),
                version.tenantId().toString(),
                version.workflowId().toString(),
                version.version(),
                version.definition().name(),
                tasksJson,
                Timestamp.from(version.createdAt())
        );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "Expected exactly one workflow version to be inserted"
            );
        }
    }

    @Override
    public Optional<WorkflowVersion> findVersion(
            UUID tenantId,
            UUID workflowId,
            int version
    ) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(workflowId, "Workflow ID must not be null");

        if (version < 1) {
            throw new IllegalArgumentException(
                    "Definition version must be at least 1"
            );
        }

        List<WorkflowVersion> matches = jdbcTemplate.query(
                """
                SELECT
                    id,
                    tenant_id,
                    workflow_id,
                    definition_version,
                    name,
                    tasks_json,
                    created_at
                FROM workflow_definition
                WHERE tenant_id = ?
                  AND workflow_id = ?
                  AND definition_version = ?
                """,
                this::mapRow,
                tenantId.toString(),
                workflowId.toString(),
                version
        );

        return matches.stream().findFirst();
    }

    private WorkflowVersion mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        WorkflowDefinition definition = new WorkflowDefinition(
                resultSet.getString("name"),
                jsonCodec.decode(resultSet.getString("tasks_json"))
        );

        return new WorkflowVersion(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("tenant_id")),
                UUID.fromString(resultSet.getString("workflow_id")),
                resultSet.getInt("definition_version"),
                definition,
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}