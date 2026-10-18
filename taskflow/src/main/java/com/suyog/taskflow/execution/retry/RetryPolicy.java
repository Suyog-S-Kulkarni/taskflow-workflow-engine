package com.suyog.taskflow.execution.retry;

import java.time.Duration;

public interface RetryPolicy {

    boolean shouldRetry(
            int failedAttempt,
            Exception failure
    );

    Duration delayAfterFailure(int failedAttempt);
}