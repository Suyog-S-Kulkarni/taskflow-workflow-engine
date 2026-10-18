package com.suyog.taskflow.execution.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffRetryPolicyTest {

    @Test
    void retriesExplicitlyRetryableFailuresBeforeLimit() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(1)
        );

        Exception failure =
                new RetryableTaskException("Temporary failure");

        assertTrue(policy.shouldRetry(1, failure));
        assertTrue(policy.shouldRetry(2, failure));
    }

    @Test
    void stopsAtMaximumTotalAttempts() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(1)
        );

        Exception failure =
                new RetryableTaskException("Temporary failure");

        assertFalse(policy.shouldRetry(3, failure));
        assertFalse(policy.shouldRetry(4, failure));

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.delayAfterFailure(3)
        );
    }

    @Test
    void doesNotRetryOrdinaryFailuresOrInterruption() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(1)
        );

        assertFalse(
                policy.shouldRetry(
                        1,
                        new IllegalArgumentException("Invalid input")
                )
        );

        assertFalse(
                policy.shouldRetry(
                        1,
                        new IllegalStateException("Programming error")
                )
        );

        assertFalse(
                policy.shouldRetry(
                        1,
                        new InterruptedException("Execution interrupted")
                )
        );
    }

    @Test
    void doublesDelayUntilMaximumDelayIsReached() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                5,
                Duration.ofMillis(100),
                Duration.ofMillis(250)
        );

        assertEquals(
                Duration.ofMillis(100),
                policy.delayAfterFailure(1)
        );

        assertEquals(
                Duration.ofMillis(200),
                policy.delayAfterFailure(2)
        );

        assertEquals(
                Duration.ofMillis(250),
                policy.delayAfterFailure(3)
        );

        assertEquals(
                Duration.ofMillis(250),
                policy.delayAfterFailure(4)
        );
    }

    @Test
    void allowsOneAttemptWithNoRetries() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                1,
                Duration.ZERO,
                Duration.ZERO
        );

        assertFalse(
                policy.shouldRetry(
                        1,
                        new RetryableTaskException("Temporary failure")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.delayAfterFailure(1)
        );
    }

    @Test
    void supportsZeroDelayForDeterministicExecutionTests() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                3,
                Duration.ZERO,
                Duration.ZERO
        );

        assertEquals(
                Duration.ZERO,
                policy.delayAfterFailure(1)
        );

        assertEquals(
                Duration.ZERO,
                policy.delayAfterFailure(2)
        );
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialBackoffRetryPolicy(
                        0,
                        Duration.ZERO,
                        Duration.ZERO
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialBackoffRetryPolicy(
                        11,
                        Duration.ZERO,
                        Duration.ZERO
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialBackoffRetryPolicy(
                        3,
                        Duration.ofMillis(-1),
                        Duration.ofSeconds(1)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialBackoffRetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(1)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialBackoffRetryPolicy(
                        3,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(31)
                )
        );
    }

    @Test
    void rejectsAttemptNumbersBelowOne() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldRetry(
                        0,
                        new RetryableTaskException("Temporary failure")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.delayAfterFailure(0)
        );
    }
}