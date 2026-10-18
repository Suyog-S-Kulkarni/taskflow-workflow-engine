package com.suyog.taskflow.execution.retry;

import java.time.Duration;
import java.util.Objects;

public final class ExponentialBackoffRetryPolicy
        implements RetryPolicy {

    private static final int MAX_ALLOWED_ATTEMPTS = 10;

    private static final Duration MAX_ALLOWED_DELAY =
            Duration.ofSeconds(30);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;

    public ExponentialBackoffRetryPolicy(
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay
    ) {
        Objects.requireNonNull(
                initialDelay,
                "Initial delay must not be null"
        );

        Objects.requireNonNull(
                maxDelay,
                "Maximum delay must not be null"
        );

        if (maxAttempts < 1 || maxAttempts > MAX_ALLOWED_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be between 1 and "
                            + MAX_ALLOWED_ATTEMPTS
            );
        }

        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "Initial delay must not be negative"
            );
        }

        if (maxDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "Maximum delay must not be negative"
            );
        }

        if (initialDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException(
                    "Initial delay must not exceed maximum delay"
            );
        }

        if (maxDelay.compareTo(MAX_ALLOWED_DELAY) > 0) {
            throw new IllegalArgumentException(
                    "Maximum delay must not exceed 30 seconds"
            );
        }

        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public boolean shouldRetry(
            int failedAttempt,
            Exception failure
    ) {
        validateAttempt(failedAttempt);

        Objects.requireNonNull(
                failure,
                "Failure must not be null"
        );

        return failedAttempt < maxAttempts
                && failure instanceof RetryableTaskException;
    }

    @Override
    public Duration delayAfterFailure(int failedAttempt) {
        validateAttempt(failedAttempt);

        if (failedAttempt >= maxAttempts) {
            throw new IllegalArgumentException(
                    "No retry is available after attempt "
                            + failedAttempt
            );
        }

        Duration delay = initialDelay;

        for (int attempt = 1; attempt < failedAttempt; attempt++) {
            delay = delay.multipliedBy(2);

            if (delay.compareTo(maxDelay) >= 0) {
                return maxDelay;
            }
        }

        return delay;
    }

    private void validateAttempt(int failedAttempt) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException(
                    "Attempt number must be at least 1"
            );
        }
    }
}