package com.suyog.taskflow.workflow.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowValidatorTest {

    private final WorkflowValidator validator = new WorkflowValidator();

    @Test
    void acceptsValidWorkflowWithParallelBranches() {
        WorkflowDefinition workflow = workflow(
                task("validate"),
                task("payments", "validate"),
                task("settlements", "validate"),
                task("reconcile", "payments", "settlements"),
                task("report", "reconcile")
        );

        List<String> order =
                validator.validateAndGetExecutionOrder(workflow);

        assertEquals(5, order.size());
        assertTrue(order.containsAll(
                List.of(
                        "validate",
                        "payments",
                        "settlements",
                        "reconcile",
                        "report"
                )
        ));

        assertBefore(order, "validate", "payments");
        assertBefore(order, "validate", "settlements");
        assertBefore(order, "payments", "reconcile");
        assertBefore(order, "settlements", "reconcile");
        assertBefore(order, "reconcile", "report");
    }

    @Test
    void acceptsTasksDeclaredBeforeTheirDependencies() {
        WorkflowDefinition workflow = workflow(
                task("report", "payments"),
                task("payments")
        );

        assertEquals(
                List.of("payments", "report"),
                validator.validateAndGetExecutionOrder(workflow)
        );
    }

    @Test
    void acceptsIndependentTasks() {
        WorkflowDefinition workflow = workflow(
                task("payments"),
                task("settlements")
        );

        assertEquals(
                List.of("payments", "settlements"),
                validator.validateAndGetExecutionOrder(workflow)
        );
    }

    @Test
    void rejectsMissingDependency() {
        WorkflowDefinition workflow = workflow(
                task("report", "missing-task")
        );

        WorkflowValidationException exception = assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(workflow)
        );

        assertTrue(exception.getMessage().contains("Unknown dependency"));
        assertTrue(exception.getMessage().contains("missing-task"));
    }

    @Test
    void rejectsCircularDependencies() {
        WorkflowDefinition workflow = workflow(
                task("a", "c"),
                task("b", "a"),
                task("c", "b")
        );

        WorkflowValidationException exception = assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(workflow)
        );

        assertTrue(
                exception.getMessage().contains("circular dependency")
        );
    }

    @Test
    void rejectsCycleEvenWhenAnotherBranchIsValid() {
        WorkflowDefinition workflow = workflow(
                task("independent"),
                task("a", "b"),
                task("b", "a")
        );

        assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(workflow)
        );
    }

    @Test
    void rejectsDuplicateTaskIds() {
        WorkflowDefinition workflow = workflow(
                task("payments"),
                task("payments")
        );

        WorkflowValidationException exception = assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(workflow)
        );

        assertTrue(exception.getMessage().contains("Duplicate task ID"));
    }

    @Test
    void rejectsSelfDependency() {
        WorkflowDefinition workflow = workflow(
                task("payments", "payments")
        );

        assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(workflow)
        );
    }

    @Test
    void rejectsDuplicateDependencies() {
        WorkflowDefinition workflow = workflow(
                task("payments"),
                task("report", "payments", "payments")
        );

        assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(workflow)
        );
    }

    @Test
    void rejectsEmptyWorkflow() {
        assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(workflow())
        );
    }

    @Test
    void rejectsBlankTaskId() {
        assertThrows(
                WorkflowValidationException.class,
                () -> validator.validateAndGetExecutionOrder(
                        workflow(task(" "))
                )
        );
    }

    @Test
    void protectsDefinitionsFromExternalListChanges() {
        List<String> dependencies =
                new ArrayList<>(List.of("payments"));

        TaskDefinition report =
                new TaskDefinition("report", "MOCK", dependencies);

        dependencies.clear();

        List<TaskDefinition> tasks = new ArrayList<>(
                List.of(task("payments"), report)
        );

        WorkflowDefinition workflow =
                new WorkflowDefinition("daily-report", tasks);

        tasks.clear();

        assertEquals(List.of("payments"), report.dependsOn());
        assertEquals(2, workflow.tasks().size());

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.dependsOn().add("another-task")
        );
    }

    private static TaskDefinition task(
            String id,
            String... dependencies
    ) {
        return new TaskDefinition(
                id,
                "MOCK",
                List.of(dependencies)
        );
    }

    private static WorkflowDefinition workflow(
            TaskDefinition... tasks
    ) {
        return new WorkflowDefinition(
                "daily-report",
                List.of(tasks)
        );
    }

    private static void assertBefore(
            List<String> order,
            String prerequisite,
            String dependant
    ) {
        assertTrue(
                order.contains(prerequisite)
                        && order.contains(dependant)
                        && order.indexOf(prerequisite)
                        < order.indexOf(dependant),
                prerequisite + " must appear before " + dependant
        );
    }
}