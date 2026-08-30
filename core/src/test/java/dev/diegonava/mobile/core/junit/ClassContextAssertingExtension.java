package dev.diegonava.mobile.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Asserts {@link DriverExtension#classContext} against whatever context JUnit hands it.
 *
 * <p>Lives in this package rather than beside the probe it is attached to, because
 * {@code classContext} is package-private — it is an implementation detail of the extension, and
 * widening it for a test would be the wrong trade.
 *
 * <p>Every assertion is made inside the run, and nothing is recorded in a static field. The probe
 * class is executed by {@code EngineTestKit} from more than one test, potentially concurrently;
 * shared state here would be raced on and would make the failure it reports arbitrary.
 */
public class ClassContextAssertingExtension implements BeforeEachCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(ClassContextAssertingExtension.class);
    private static final String COUNT = "tests-seen";

    /** How many invocations the probe class is expected to produce. */
    public static final int EXPECTED_INVOCATIONS = 3;

    @Override
    public void beforeEach(ExtensionContext context) {
        ExtensionContext classContext = DriverExtension.classContext(context);

        assertThat(classContext.getTestMethod())
                .as("the class context must not be a method context")
                .isEmpty();
        assertThat(classContext.getTestClass()).isPresent();

        ExtensionContext.Store store = classContext.getStore(NS);
        store.put(COUNT, store.getOrDefault(COUNT, Integer.class, 0) + 1);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // The per-test checks above would pass just as happily if every invocation had its own
        // store — each would simply see zero and write one. Only reading the store once, at the
        // end, can tell one shared store from three private ones.
        assertThat(context.getStore(NS).getOrDefault(COUNT, Integer.class, 0))
                .as("every invocation must have incremented one store, held on the class context")
                .isEqualTo(EXPECTED_INVOCATIONS);
    }
}
