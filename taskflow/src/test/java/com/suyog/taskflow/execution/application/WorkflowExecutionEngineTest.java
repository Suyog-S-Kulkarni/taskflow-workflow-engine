package com.suyog.taskflow.execution.application;

import com.suyog.taskflow.execution.domain.TaskStatus;
import com.suyog.taskflow.execution.domain.WorkflowExecutionReport;
import com.suyog.taskflow.handler.TaskHandler;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowValidationException;
import com.suyog.taskflow.workflow.domain.WorkflowValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class WorkflowExecutionEngineTest {

    private final ExecutorService workers =
            Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownWorkers() {
        workers.shutdownNow();
    }

    @Test
    void executesDependenciesBeforeTheirDependants() throws Exception {
        Set<String> completed = ConcurrentHashMap.newKeySet();

        TaskHandler handler = task -> {
            if (!completed.containsAll(task.dependsOn())) {
                throw new IllegalStateException(
                        "A prerequisite has not completed"
                );
            }

            completed.add(task.id());
        };

        WorkflowExecutionEngine engine = engine(handler, 2);

        WorkflowExecutionReport report = engine.execute(workflow(
                task("report", "reconcile"),
                task("reconcile", "payments"),
                task("payments")
        ));

        assertTrue(report.succeeded());

        assertEquals(
                Set.of("payments", "reconcile", "report"),
                completed
        );
    }

    @Test
    void overlapsIndependentTasksAndRespectsConcurrencyLimit()
            throws Exception {

        CountDownLatch firstTwoStarted = new CountDownLatch(2);

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();

        Set<String> completed = ConcurrentHashMap.newKeySet();

        TaskHandler handler = task -> {
            int current = active.incrementAndGet();

            maximumActive.accumulateAndGet(current, Math::max);

            try {
                if (task.id().equals("payments")
                        || task.id().equals("settlements")) {

                    firstTwoStarted.countDown();

                    if (!firstTwoStarted.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Independent tasks did not overlap"
                        );
                    }
                }

                if (!completed.containsAll(task.dependsOn())) {
                    throw new IllegalStateException(
                            "Task started before its prerequisites"
                    );
                }

                completed.add(task.id());

            } finally {
                active.decrementAndGet();
            }
        };

        WorkflowExecutionReport report =
                engine(handler, 2).execute(workflow(
                        task("payments"),
                        task("settlements"),
                        task("independent"),
                        task("reconcile", "payments", "settlements"),
                        task("report", "reconcile")
                ));

        assertTrue(report.succeeded());
        assertEquals(2, maximumActive.get());
        assertEquals(0, active.get());
        assertEquals(5, completed.size());
    }

    @Test
    void skipsFailedBranchButCompletesIndependentBranch()
            throws Exception {

        Set<String> executed = ConcurrentHashMap.newKeySet();

        TaskHandler handler = task -> {
            executed.add(task.id());

            if (task.id().equals("payments")) {
                throw new IllegalStateException(
                        "Simulated processor failure"
                );
            }
        };

        WorkflowExecutionReport report =
                engine(handler, 2).execute(workflow(
                        task("payments"),
                        task("settlements"),
                        task("reconcile", "payments", "settlements"),
                        task("report", "reconcile"),
                        task("independent")
                ));

        assertFalse(report.succeeded());

        assertEquals(
                TaskStatus.FAILED,
                report.taskStatuses().get("payments")
        );

        assertEquals(
                TaskStatus.SUCCEEDED,
                report.taskStatuses().get("settlements")
        );

        assertEquals(
                TaskStatus.SUCCEEDED,
                report.taskStatuses().get("independent")
        );

        assertEquals(
                TaskStatus.SKIPPED,
                report.taskStatuses().get("reconcile")
        );

        assertEquals(
                TaskStatus.SKIPPED,
                report.taskStatuses().get("report")
        );

        assertFalse(executed.contains("reconcile"));
        assertFalse(executed.contains("report"));
    }

    @Test
    void rejectsUnsupportedTypesBeforeExecutingAnyTask() {
        AtomicInteger executions = new AtomicInteger();

        TaskHandler handler = task -> executions.incrementAndGet();

        WorkflowDefinition workflow = workflow(
                task("supported"),
                new TaskDefinition(
                        "unsupported",
                        "UNKNOWN",
                        List.of()
                )
        );

        assertThrows(
                WorkflowValidationException.class,
                () -> engine(handler, 2).execute(workflow)
        );

        assertEquals(0, executions.get());
    }

    @Test
    void rejectsCyclesBeforeExecutingAnyTask() {
        AtomicInteger executions = new AtomicInteger();

        TaskHandler handler = task -> executions.incrementAndGet();

        assertThrows(
                WorkflowValidationException.class,
                () -> engine(handler, 2).execute(workflow(
                        task("a", "b"),
                        task("b", "a")
                ))
        );

        assertEquals(0, executions.get());
    }

    @Test
    void rejectsInvalidParallelism() {
        TaskHandler handler = task -> {
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> engine(handler, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine(handler, 33)
        );
    }

    private WorkflowExecutionEngine engine(
            TaskHandler handler,
            int parallelism
    ) {
        return new WorkflowExecutionEngine(
                new WorkflowValidator(),
                Map.of("MOCK", handler),
                parallelism,
                workers
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
                "execution-test",
                List.of(tasks)
        );
    }
}