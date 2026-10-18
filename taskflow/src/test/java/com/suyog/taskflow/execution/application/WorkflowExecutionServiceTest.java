package com.suyog.taskflow.execution.application;

import com.suyog.taskflow.execution.domain.WorkflowExecutionReport;
import com.suyog.taskflow.handler.TaskHandler;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowExecutionServiceTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void rejectsConcurrentRunAndReleasesSlotAfterCompletion()
            throws Exception {

        ExecutorService workers = Executors.newFixedThreadPool(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        TaskHandler handler = task -> {
            started.countDown();

            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Test release timed out"
                );
            }
        };

        WorkflowExecutionEngine engine = new WorkflowExecutionEngine(
                new WorkflowValidator(),
                Map.of("MOCK", handler),
                1,
                workers
        );

        WorkflowExecutionService service =
                new WorkflowExecutionService(engine);

        WorkflowDefinition workflow = new WorkflowDefinition(
                "admission-test",
                List.of(
                        new TaskDefinition(
                                "task",
                                "MOCK",
                                List.of()
                        )
                )
        );

        try {
            Future<WorkflowExecutionReport> first =
                    caller.submit(
                            () -> service.executePreview(workflow)
                    );

            assertTrue(started.await(3, TimeUnit.SECONDS));

            assertThrows(
                    WorkflowBusyException.class,
                    () -> service.executePreview(workflow)
            );

            release.countDown();

            assertTrue(
                    first.get(3, TimeUnit.SECONDS).succeeded()
            );

            // The previous execution released its admission slot.
            assertTrue(
                    service.executePreview(workflow).succeeded()
            );

        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }
}