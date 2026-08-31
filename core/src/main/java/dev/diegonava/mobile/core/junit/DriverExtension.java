package dev.diegonava.mobile.core.junit;

import dev.diegonava.mobile.core.device.AppLifecycle;
import dev.diegonava.mobile.core.device.DevicePool;
import dev.diegonava.mobile.core.device.DeviceSlot;
import dev.diegonava.mobile.core.driver.DriverManager;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leases a device, opens a session, and guarantees both go back.
 *
 * <p>The lease and the session are released in a {@code finally}, so a test that fails, errors, or
 * times out still returns its slot to the pool. A leaked slot in a pool of two means the next test
 * blocks for five minutes and then fails for a reason that has nothing to do with the test — the
 * kind of bug that gets misdiagnosed as "Appium being flaky" for weeks.
 *
 * <p>Where that lifecycle sits is the class's choice, via {@link MobileTest#session()}. Per test is
 * the default and assumes nothing. Per class opens once and resets the app in between, which is
 * only safe because the reset is the same reinstall Appium would have done between sessions —
 * see {@link SessionScope#PER_CLASS}.
 *
 * <p>Both scopes run on one thread per class ({@code parallel.mode.default=same_thread}, classes
 * concurrent), which is what lets {@link DriverManager}'s thread-local hold a session opened in
 * {@code beforeAll} and read in a test method.
 */
public final class DriverExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(DriverExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DriverExtension.class);
    private static final String SLOT_KEY = "device-slot";
    private static final String TESTS_RUN_KEY = "tests-run";

    @Override
    public void beforeAll(ExtensionContext context) {
        if (scopeOf(context) == SessionScope.PER_CLASS) {
            acquire(context);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        CurrentTest.set(context.getRequiredTestClass().getSimpleName() + "."
                + context.getRequiredTestMethod().getName());

        if (scopeOf(context) == SessionScope.PER_TEST) {
            acquire(context);
            return;
        }

        ExtensionContext.Store classStore = classContext(context).getStore(NAMESPACE);
        int alreadyRun = classStore.getOrDefault(TESTS_RUN_KEY, Integer.class, 0);
        classStore.put(TESTS_RUN_KEY, alreadyRun + 1);

        // A session that died takes the rest of the class with it unless something notices. The
        // test that killed it has already failed and reported why; the ones after it would fail
        // with a stale-session error naming nothing, which is the confusing half. A session that
        // has just been opened needs no reset, for the same reason the first test does not.
        if (!DriverManager.isOpen()) {
            log.warn("The per-class session is gone; opening a new one for {}", CurrentTest.id());
            DriverManager.open(classStore.get(SLOT_KEY, DeviceSlot.class));
            return;
        }

        // Not before the first test: the session was created moments ago and the app is already
        // in the state this would put it in, so paying for a reinstall here buys nothing.
        if (alreadyRun > 0) {
            AppLifecycle.resetToCleanState();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        try {
            if (scopeOf(context) == SessionScope.PER_TEST) {
                release(context);
            }
        } finally {
            CurrentTest.clear();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (scopeOf(context) == SessionScope.PER_CLASS) {
            release(context);
        }
    }

    /**
     * The context the class-scoped session is stored on.
     *
     * <p>Not {@code getParent()}. That is the class only for a plain {@code @Test}; a retried
     * {@code @Flaky} test runs as a {@code @TestTemplate}, which inserts a container between the
     * invocation and the class. Reading the slot from the wrong level finds nothing, and the
     * symptom would have been the retry — the one path that is already only exercised when
     * something else has gone wrong.
     */
    // Package-private so DriverExtensionClassContextTest can assert it against a real hierarchy.
    static ExtensionContext classContext(ExtensionContext context) {
        ExtensionContext current = context;
        while (current.getTestMethod().isPresent()) {
            current = current.getParent().orElseThrow();
        }
        return current;
    }

    private static SessionScope scopeOf(ExtensionContext context) {
        return AnnotationSupport.findAnnotation(context.getRequiredTestClass(), MobileTest.class)
                .map(MobileTest::session)
                // Registered by hand rather than through @MobileTest. Assume nothing.
                .orElse(SessionScope.PER_TEST);
    }

    /** Leases a slot and opens a session on it, returning the slot if the session cannot start. */
    private static void acquire(ExtensionContext context) {
        DeviceSlot slot = DevicePool.get().lease();
        context.getStore(NAMESPACE).put(SLOT_KEY, slot);
        try {
            DriverManager.open(slot);
        } catch (RuntimeException e) {
            // The session failed to start, so nothing downstream will release the slot.
            DevicePool.get().release(slot);
            context.getStore(NAMESPACE).remove(SLOT_KEY);
            throw e;
        }
    }

    private static void release(ExtensionContext context) {
        DeviceSlot slot = context.getStore(NAMESPACE).remove(SLOT_KEY, DeviceSlot.class);
        try {
            DriverManager.close();
        } finally {
            // Null when the session never started: acquire() has already returned the slot, and
            // JUnit still calls the after callback for a before callback that threw.
            if (slot != null) {
                DevicePool.get().release(slot);
            }
        }
    }
}
