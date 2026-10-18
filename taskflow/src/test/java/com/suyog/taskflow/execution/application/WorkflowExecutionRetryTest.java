package com.suyog.taskflow.execution.application;

import com.suyog.taskflow.execution.domain.TaskStatus;
import com.suyog.taskflow.execution.domain.WorkflowExecutionReport;
import com.suyog.taskflow.execution.retry.ExponentialBackoffRetryPolicy;
import com.suyog.taskflow.execution.retry.RetryableTaskException;
import com.suyog.taskflow.handler.TaskHandler;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class WorkflowExecutionRetryTest {

    private final ExecutorService workers =
            Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownWorkers() {
        workers.shutdownNow();
    }

    @Test
    void retriesUntilSuccessAndThenRunsDependant() throws Exception {
        AtomicBoolean prerequisiteSucceeded = new AtomicBoolean(false);

        TaskHandler handler = attemptAware((task, attempt) -> {
            if (task.id().equals("fetch")) {
                if (attempt < 3) {
                    throw new RetryableTaskException("Temporary failure");
                }

                prerequisiteSucceeded.set(true);

            } else if (!prerequisiteSucceeded.get()) {
                throw new IllegalStateException(
                        "Dependant started before successful prerequisite"
                );
            }
        });

        WorkflowExecutionEngine engine = engine(
                handler,
                3,
                Duration.ZERO
        );

        WorkflowDefinition workflow = workflow(
                task("fetch"),
                task("report", "fetch")
        );

        WorkflowExecutionReport report = engine.execute(workflow);

        assertTrue(report.succeeded());
        assertEquals(3, report.attemptCounts().get("fetch").intValue());
        assertEquals(1, report.attemptCounts().get("report").intValue());

        // Attempt numbers belong to each run, not the shared handler.
        prerequisiteSucceeded.set(false);

        WorkflowExecutionReport repeatedRun = engine.execute(workflow);

        assertTrue(repeatedRun.succeeded());
        assertEquals(
                3,
                repeatedRun.attemptCounts().get("fetch").intValue()
        );
    }

    @Test
    void exhaustsRetriesAndSkipsDependant() throws Exception {
        TaskHandler handler = task -> {
            if (task.id().equals("fetch")) {
                throw new RetryableTaskException("Still unavailable");
            }
        };

        WorkflowExecutionReport report =
                engine(handler, 3, Duration.ZERO).execute(workflow(
                        task("fetch"),
                        task("report", "fetch"),
                        task("independent")
                ));

        assertFalse(report.succeeded());

        assertEquals(
                TaskStatus.FAILED,
                report.taskStatuses().get("fetch")
        );

        assertEquals(
                TaskStatus.SKIPPED,
                report.taskStatuses().get("report")
        );

        assertEquals(
                TaskStatus.SUCCEEDED,
                report.taskStatuses().get("independent")
        );

        assertEquals(3, report.attemptCounts().get("fetch").intValue());
        assertEquals(0, report.attemptCounts().get("report").intValue());
        assertEquals(
                1,
                report.attemptCounts().get("independent").intValue()
        );
    }

    @Test
    void doesNotRetryPermanentFailure() throws Exception {
        TaskHandler handler = task -> {
            throw new IllegalStateException("Permanent failure");
        };

        WorkflowExecutionReport report =
                engine(handler, 3, Duration.ZERO).execute(workflow(
                        task("fetch")
                ));

        assertFalse(report.succeeded());

        assertEquals(
                TaskStatus.FAILED,
                report.taskStatuses().get("fetch")
        );

        assertEquals(1, report.attemptCounts().get("fetch").intValue());
    }

    @Test
    void schedulesRetryWhileAnotherTaskIsStillRunning()
            throws Exception {

        CountDownLatch retrySucceeded = new CountDownLatch(1);

        TaskHandler handler = new TaskHandler() {

            @Override
            public void execute(TaskDefinition task) throws Exception {
                execute(task, 1);
            }

            @Override
            public void execute(
                    TaskDefinition task,
                    int attempt
            ) throws Exception {

                if (task.id().equals("flaky")) {
                    if (attempt == 1) {
                        throw new RetryableTaskException(
                                "First attempt fails"
                        );
                    }

                    retrySucceeded.countDown();

                } else if (!retrySucceeded.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Retry did not execute while this task waited"
                    );
                }
            }
        };

        WorkflowExecutionReport report =
                engine(handler, 3, Duration.ofMillis(50))
                        .execute(workflow(
                                task("flaky"),
                                task("waiting-task")
                        ));

        assertTrue(report.succeeded());

        assertEquals(
                2,
                report.attemptCounts().get("flaky").intValue()
        );

        assertEquals(
                1,
                report.attemptCounts().get("waiting-task").intValue()
        );
    }

    @Test
    void honoursOneAttemptConfiguration() throws Exception {
        TaskHandler handler = task -> {
            throw new RetryableTaskException("Temporary failure");
        };

        WorkflowExecutionReport report =
                engine(handler, 1, Duration.ZERO).execute(workflow(
                        task("fetch")
                ));

        assertFalse(report.succeeded());
        assertEquals(1, report.attemptCounts().get("fetch").intValue());
    }

    private WorkflowExecutionEngine engine(
            TaskHandler handler,
            int maxAttempts,
            Duration delay
    ) {
        return new WorkflowExecutionEngine(
                new WorkflowValidator(),
                Map.of("MOCK", handler),
                2,
                workers,
                new ExponentialBackoffRetryPolicy(
                        maxAttempts,
                        delay,
                        delay
                )
        );
    }

    private static TaskHandler attemptAware(
            BiConsumer<TaskDefinition, Integer> action
    ) {
        return new TaskHandler() {

            @Override
            public void execute(TaskDefinition task) {
                action.accept(task, 1);
            }

            @Override
            public void execute(
                    TaskDefinition task,
                    int attempt
            ) {
                action.accept(task, attempt);
            }
        };
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
                "retry-test",
                List.of(tasks)
        );
    }
}