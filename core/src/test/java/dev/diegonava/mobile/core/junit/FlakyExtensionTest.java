package dev.diegonava.mobile.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.flake.FlakeLedger;
import dev.diegonava.mobile.core.flake.FlakeRecord;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * A self-test for the retry machinery — no device involved.
 *
 * <p>Retry logic is the kind of thing that looks obviously correct and is quietly off by one. This
 * drives the real extension with a counter that fails a known number of times, then inspects the
 * ledger it produced.
 */
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlakyExtensionTest {

    private static final AtomicInteger FAILS_THEN_PASSES = new AtomicInteger();
    private static final AtomicInteger ALWAYS_STABLE = new AtomicInteger();

    @Flaky(
            maxAttempts = 3,
            reason = "self-test: deliberately fails twice to exercise the retry path",
            expires = "2099-01-01")
    @Order(1)
    @DisplayName("a test that fails twice then passes is retried until it passes")
    void retriesUntilItPasses() {
        int attempt = FAILS_THEN_PASSES.incrementAndGet();
        assertThat(attempt).as("deliberate failure on attempts 1 and 2").isGreaterThanOrEqualTo(3);
    }

    @Flaky(maxAttempts = 3, reason = "self-test: never actually fails", expires = "2099-01-01")
    @Order(2)
    @DisplayName("a stable quarantined test runs exactly once")
    void stableTestRunsOnce() {
        ALWAYS_STABLE.incrementAndGet();
        assertThat(true).isTrue();
    }

    @AfterAll
    static void assertLedgerRecordedTheTruth() {
        assertThat(FAILS_THEN_PASSES.get())
                .as("the flaky test should have been invoked exactly three times")
                .isEqualTo(3);

        assertThat(ALWAYS_STABLE.get())
                .as("a passing test must not be retried — retries are only spent on failure")
                .isEqualTo(1);

        FlakeRecord retried = ledgerEntryFor("FlakyExtensionTest.retriesUntilItPasses");
        assertThat(retried.attempts()).isEqualTo(3);
        assertThat(retried.passed()).isTrue();
        assertThat(retried.passedFirstAttempt()).isFalse();
        assertThat(retried.failures()).hasSize(2);

        FlakeRecord stable = ledgerEntryFor("FlakyExtensionTest.stableTestRunsOnce");
        assertThat(stable.attempts()).isEqualTo(1);
        assertThat(stable.passedFirstAttempt())
                .as("passing first try is the signal that quarantine can be lifted")
                .isTrue();
        assertThat(stable.failures()).isEmpty();
    }

    private static FlakeRecord ledgerEntryFor(String testId) {
        return FlakeLedger.records().stream()
                .filter(record -> record.testId().equals(testId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No flake ledger entry for " + testId));
    }
}
