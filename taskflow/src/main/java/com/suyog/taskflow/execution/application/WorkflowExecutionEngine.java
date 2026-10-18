package com.suyog.taskflow.execution.application;

import com.suyog.taskflow.execution.domain.TaskStatus;
import com.suyog.taskflow.execution.domain.WorkflowExecutionReport;
import com.suyog.taskflow.execution.retry.ExponentialBackoffRetryPolicy;
import com.suyog.taskflow.execution.retry.RetryPolicy;
import com.suyog.taskflow.handler.TaskHandler;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowValidationException;
import com.suyog.taskflow.workflow.domain.WorkflowValidator;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class WorkflowExecutionEngine {

    private final WorkflowValidator validator;
    private final Map<String, TaskHandler> handlers;
    private final int parallelism;
    private final ExecutorService workers;
    private final RetryPolicy retryPolicy;

    // Preserves compatibility with existing tests.
    // This constructor allows one attempt and no retries.
    public WorkflowExecutionEngine(
            WorkflowValidator validator,
            Map<String, TaskHandler> handlers,
            int parallelism,
            ExecutorService workers
    ) {
        this(
                validator,
                handlers,
                parallelism,
                workers,
                new ExponentialBackoffRetryPolicy(
                        1,
                        Duration.ZERO,
                        Duration.ZERO
                )
        );
    }

    public WorkflowExecutionEngine(
            WorkflowValidator validator,
            Map<String, TaskHandler> handlers,
            int parallelism,
            ExecutorService workers,
            RetryPolicy retryPolicy
    ) {
        this.validator = Objects.requireNonNull(
                validator,
                "Validator must not be null"
        );

        this.handlers = Map.copyOf(
                Objects.requireNonNull(
                        handlers,
                        "Handlers must not be null"
                )
        );

        this.workers = Objects.requireNonNull(
                workers,
                "Workers must not be null"
        );

        this.retryPolicy = Objects.requireNonNull(
                retryPolicy,
                "Retry policy must not be null"
        );

        if (parallelism < 1 || parallelism > 32) {
            throw new IllegalArgumentException(
                    "Parallelism must be between 1 and 32"
            );
        }

        this.parallelism = parallelism;
    }

    public WorkflowExecutionReport execute(
            WorkflowDefinition workflow
    ) throws InterruptedException {

        validator.validateAndGetExecutionOrder(workflow);

        Map<String, TaskDefinition> tasksById = new LinkedHashMap<>();

        for (TaskDefinition task : workflow.tasks()) {
            if (!handlers.containsKey(task.type())) {
                throw new WorkflowValidationException(
                        "Unsupported task type: " + task.type()
                );
            }

            tasksById.put(task.id(), task);
        }

        Map<String, List<String>> dependants = new HashMap<>();
        Map<String, Integer> remainingDependencies = new HashMap<>();
        Map<String, TaskStatus> statuses = new LinkedHashMap<>();
        Map<String, Integer> attemptCounts = new LinkedHashMap<>();

        for (TaskDefinition task : tasksById.values()) {
            dependants.put(task.id(), new ArrayList<>());

            remainingDependencies.put(
                    task.id(),
                    task.dependsOn().size()
            );

            statuses.put(task.id(), TaskStatus.BLOCKED);
            attemptCounts.put(task.id(), 0);
        }

        for (TaskDefinition task : tasksById.values()) {
            for (String dependencyId : task.dependsOn()) {
                dependants.get(dependencyId).add(task.id());
            }
        }

        Queue<String> readyTasks = new ArrayDeque<>();
        DelayQueue<ScheduledRetry> delayedRetries = new DelayQueue<>();

        for (String taskId : tasksById.keySet()) {
            if (remainingDependencies.get(taskId) == 0) {
                statuses.put(taskId, TaskStatus.READY);
                readyTasks.add(taskId);
            }
        }

        CompletionService<TaskCompletion> completions =
                new ExecutorCompletionService<>(workers);

        List<Future<TaskCompletion>> submittedTasks = new ArrayList<>();

        int inFlight = 0;

        try {
            while (!readyTasks.isEmpty()
                    || inFlight > 0
                    || !delayedRetries.isEmpty()) {

                // Move eligible retries back to the ready queue.
                ScheduledRetry eligibleRetry;

                while ((eligibleRetry = delayedRetries.poll()) != null) {
                    statuses.put(
                            eligibleRetry.taskId(),
                            TaskStatus.READY
                    );

                    readyTasks.add(eligibleRetry.taskId());
                }

                // Dispatch only up to the configured concurrency limit.
                while (!readyTasks.isEmpty() && inFlight < parallelism) {
                    String taskId = readyTasks.remove();
                    TaskDefinition task = tasksById.get(taskId);

                    int attempt = attemptCounts.get(taskId) + 1;

                    attemptCounts.put(taskId, attempt);
                    statuses.put(taskId, TaskStatus.RUNNING);

                    submittedTasks.add(
                            completions.submit(
                                    () -> executeTask(task, attempt)
                            )
                    );

                    inFlight++;
                }

                // No workers are active, but a future retry is pending.
                // Wait on the coordinator thread, not on a worker.
                if (inFlight == 0) {
                    if (!delayedRetries.isEmpty()) {
                        ScheduledRetry retry = delayedRetries.take();

                        statuses.put(
                                retry.taskId(),
                                TaskStatus.READY
                        );

                        readyTasks.add(retry.taskId());
                    }

                    continue;
                }

                Future<TaskCompletion> completedFuture;

                if (inFlight < parallelism
                        && !delayedRetries.isEmpty()) {

                    long waitNanos = Math.max(
                            0L,
                            delayedRetries.peek()
                                    .getDelay(TimeUnit.NANOSECONDS)
                    );

                    completedFuture = completions.poll(
                            waitNanos,
                            TimeUnit.NANOSECONDS
                    );

                    if (completedFuture == null) {
                        // A retry became eligible before a worker finished.
                        continue;
                    }
                } else {
                    completedFuture = completions.take();
                }

                TaskCompletion completion;

                try {
                    completion = completedFuture.get();
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(
                            "Worker terminated unexpectedly",
                            exception.getCause()
                    );
                }

                inFlight--;

                if (completion.failure() == null) {
                    statuses.put(
                            completion.taskId(),
                            TaskStatus.SUCCEEDED
                    );

                    for (String dependantId :
                            dependants.get(completion.taskId())) {

                        int remaining =
                                remainingDependencies.get(dependantId) - 1;

                        remainingDependencies.put(
                                dependantId,
                                remaining
                        );

                        if (remaining == 0) {
                            statuses.put(
                                    dependantId,
                                    TaskStatus.READY
                            );

                            readyTasks.add(dependantId);
                        }
                    }

                } else if (retryPolicy.shouldRetry(
                        completion.attempt(),
                        completion.failure()
                )) {
                    Duration delay = retryPolicy.delayAfterFailure(
                            completion.attempt()
                    );

                    statuses.put(
                            completion.taskId(),
                            TaskStatus.RETRY_WAIT
                    );

                    delayedRetries.add(
                            new ScheduledRetry(
                                    completion.taskId(),
                                    delay
                            )
                    );

                } else {
                    statuses.put(
                            completion.taskId(),
                            TaskStatus.FAILED
                    );
                }
            }

            statuses.replaceAll((taskId, status) ->
                    status == TaskStatus.BLOCKED
                            ? TaskStatus.SKIPPED
                            : status
            );

            return new WorkflowExecutionReport(
                    workflow.name(),
                    statuses,
                    attemptCounts
            );

        } finally {
            for (Future<TaskCompletion> future : submittedTasks) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }
    }

    private TaskCompletion executeTask(
            TaskDefinition task,
            int attempt
    ) {
        try {
            handlers.get(task.type()).execute(task, attempt);

            return new TaskCompletion(task.id(), attempt, null);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return new TaskCompletion(
                    task.id(),
                    attempt,
                    exception
            );

        } catch (Exception exception) {
            return new TaskCompletion(
                    task.id(),
                    attempt,
                    exception
            );
        }
    }

    private record TaskCompletion(
            String taskId,
            int attempt,
            Exception failure
    ) {
    }

    private static final class ScheduledRetry implements Delayed {

        private final String taskId;
        private final long eligibleAtNanos;

        private ScheduledRetry(
                String taskId,
                Duration delay
        ) {
            this.taskId = taskId;

            if (delay.isNegative()
                    || delay.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException(
                        "Retry delay must be between zero and 30 seconds"
                );
            }

            this.eligibleAtNanos =
                    System.nanoTime() + delay.toNanos();
        }

        private String taskId() {
            return taskId;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(
                    eligibleAtNanos - System.nanoTime(),
                    TimeUnit.NANOSECONDS
            );
        }

        @Override
        public int compareTo(Delayed other) {
            ScheduledRetry retry = (ScheduledRetry) other;

            return Long.compare(
                    eligibleAtNanos - retry.eligibleAtNanos,
                    0L
            );
        }
    }
}