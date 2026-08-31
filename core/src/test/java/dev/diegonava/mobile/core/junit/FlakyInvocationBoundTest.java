package dev.diegonava.mobile.core.junit;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import dev.diegonava.mobile.core.junit.probes.BeforeEachCallbackThrowsProbe;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * A {@code @Flaky} test must stop, whatever fails and wherever it fails from.
 *
 * <p>{@link FlakyExtension} decides when to stop retrying from the outcomes it is told about: the
 * test body throwing, and a {@code @BeforeEach} method throwing. It is told nothing when a
 * {@code BeforeEachCallback} <em>extension</em> throws, so before the invocation bound was added
 * its attempt counter stayed at zero, its completion check stayed false, and JUnit kept being
 * handed new invocations — without end.
 *
 * <p>Which is not a corner case here. {@link DriverExtension} is a {@code BeforeEachCallback} and
 * it throws when a session will not open, the most common infrastructure failure this suite has. A
 * quarantined test on a sick device would have spun until the CI job's own timeout killed it, an
 * hour later, with nothing in the log to say why. It was found by a local run burning 6745 CPU
 * seconds on three tests that do nothing.
 *
 * <p>The bug is a non-terminating loop, so this test is time-bounded rather than merely asserted
 * on: without the fix it does not fail, it hangs, and a hung build is the thing it exists to
 * prevent.
 */
class FlakyInvocationBoundTest {

    @Test
    void a_failure_from_a_before_each_callback_stops_at_max_attempts_instead_of_looping() {
        int max = BeforeEachCallbackThrowsProbe.MAX_ATTEMPTS;

        assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> EngineTestKit.engine("junit-jupiter")
                        .selectors(selectClass(BeforeEachCallbackThrowsProbe.class))
                        .execute()
                        .testEvents()
                        // Every invocation fails and nothing tells the extension so. The count is
                        // what proves the bound did the stopping: unbounded, this never returns.
                        .assertStatistics(stats -> stats.started(max).failed(max)),
                "The @Flaky invocation bound is gone: JUnit is being handed invocations without end.");
    }
}
