package com.suyog.taskflow.config;

import com.suyog.taskflow.execution.application.WorkflowExecutionEngine;
import com.suyog.taskflow.execution.retry.ExponentialBackoffRetryPolicy;
import com.suyog.taskflow.execution.retry.RetryPolicy;
import com.suyog.taskflow.execution.retry.RetryableTaskException;
import com.suyog.taskflow.handler.TaskHandler;
import com.suyog.taskflow.workflow.domain.TaskDefinition;
import com.suyog.taskflow.workflow.domain.WorkflowValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class ExecutionConfiguration {

    @Bean(name = "workflowWorkers", destroyMethod = "close")
    public ExecutorService workflowWorkers() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean
    public RetryPolicy retryPolicy(
            @Value("${flowforge.retry.max-attempts:3}")
            int maxAttempts,

            @Value("${flowforge.retry.initial-delay-ms:100}")
            long initialDelayMs,

            @Value("${flowforge.retry.max-delay-ms:1000}")
            long maxDelayMs
    ) {
        return new ExponentialBackoffRetryPolicy(
                maxAttempts,
                Duration.ofMillis(initialDelayMs),
                Duration.ofMillis(maxDelayMs)
        );
    }

    @Bean
    public WorkflowExecutionEngine workflowExecutionEngine(
            WorkflowValidator validator,
            @Qualifier("workflowWorkers") ExecutorService workers,
            RetryPolicy retryPolicy
    ) {
        TaskHandler successfulMock = task -> {
            // Completes successfully.
        };

        TaskHandler permanentFailure = task -> {
            throw new IllegalStateException(
                    "Simulated permanent failure"
            );
        };

        TaskHandler alwaysRetryableFailure = task -> {
            throw new RetryableTaskException(
                    "Simulated temporary failure"
            );
        };

        TaskHandler retryThenSuccess = new TaskHandler() {

            @Override
            public void execute(TaskDefinition task) throws Exception {
                execute(task, 1);
            }

            @Override
            public void execute(
                    TaskDefinition task,
                    int attempt
            ) {
                if (attempt < 3) {
                    throw new RetryableTaskException(
                            "Simulated temporary failure"
                    );
                }

                // Attempt 3 succeeds.
            }
        };

        Map<String, TaskHandler> handlers = Map.of(
                "MOCK_SUCCESS", successfulMock,
                "MOCK_FAILURE", permanentFailure,
                "MOCK_RETRYABLE_FAILURE", alwaysRetryableFailure,
                "MOCK_RETRY_THEN_SUCCESS", retryThenSuccess
        );

        return new WorkflowExecutionEngine(
                validator,
                handlers,
                4,
                workers,
                retryPolicy
        );
    }
}