package dev.diegonava.mobile.core.junit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import dev.diegonava.mobile.core.junit.probes.ClassContextProbe;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * A per-class session hangs on the class's context, and every test in the class has to find the
 * same one. Which context that is, is not obvious: {@code getParent()} is the class for a plain
 * {@code @Test}, but a {@code @Flaky} test runs as a {@code @TestTemplate} and JUnit inserts a
 * container between the invocation and the class.
 *
 * <p>Get it wrong and each invocation gets its own store. Every test then reads a slot that is not
 * there and a counter that says zero — so every test believes it is the first, which is precisely
 * the one that skips the reset. On the retry path, which by definition only runs when something
 * has already gone wrong.
 *
 * <p>Asserted against a real hierarchy rather than a fake one, because the hierarchy's shape *is*
 * the thing under test; a hand-built stub would encode the same assumption twice and agree with
 * itself. The assertions live in {@link ClassContextAssertingExtension}, inside the run.
 */
class DriverExtensionClassContextTest {

    @Test
    void one_store_is_shared_by_a_plain_test_and_a_template_invocation_alike() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ClassContextProbe.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(ClassContextAssertingExtension.EXPECTED_INVOCATIONS)
                        .succeeded(ClassContextAssertingExtension.EXPECTED_INVOCATIONS)
                        .failed(0));
    }

    @Test
    void the_shared_store_is_checked_in_the_class_teardown_not_only_per_test() {
        // The teardown assertion runs in a container, so container failures have to be asserted on
        // explicitly. Test statistics alone would report three green tests whether the store was
        // shared or not — which is exactly the bug this file is about.
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ClassContextProbe.class))
                .execute()
                .containerEvents()
                .assertStatistics(stats -> stats.failed(0));
    }
}
