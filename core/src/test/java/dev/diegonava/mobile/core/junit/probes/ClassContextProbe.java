package dev.diegonava.mobile.core.junit.probes;

import dev.diegonava.mobile.core.junit.ClassContextAssertingExtension;
import dev.diegonava.mobile.core.junit.Flaky;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Run by {@code DriverExtensionClassContextTest} through {@code EngineTestKit}. Never run on its
 * own — see this package's description.
 *
 * <p>Three invocations of two different shapes, which is the whole point: two ordinary tests whose
 * context's parent is the class, and one whose annotation makes JUnit insert a {@code @TestTemplate}
 * container in between.
 */
@ExtendWith(ClassContextAssertingExtension.class)
public class ClassContextProbe {

    @Test
    void a_plain_test() {}

    @Test
    void another_plain_test() {}

    /** The case that motivated the fix: a template container sits between this and the class. */
    @Flaky(reason = "Not flaky. Declared so the @TestTemplate hierarchy is exercised.", expires = "2099-01-01")
    void a_templated_test() {}
}
