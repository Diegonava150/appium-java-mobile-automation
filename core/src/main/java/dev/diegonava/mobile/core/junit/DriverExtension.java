package dev.diegonava.mobile.core.junit;

import dev.diegonava.mobile.core.device.DevicePool;
import dev.diegonava.mobile.core.device.DeviceSlot;
import dev.diegonava.mobile.core.driver.DriverManager;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Leases a device, opens a session for the test, and guarantees both go back.
 *
 * <p>The lease and the session are released in a {@code finally}, so a test that fails, errors, or
 * times out still returns its slot to the pool. A leaked slot in a pool of two means the next test
 * blocks for five minutes and then fails for a reason that has nothing to do with the test — the
 * kind of bug that gets misdiagnosed as "Appium being flaky" for weeks.
 */
public final class DriverExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DriverExtension.class);
    private static final String SLOT_KEY = "device-slot";

    @Override
    public void beforeEach(ExtensionContext context) {
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

    @Override
    public void afterEach(ExtensionContext context) {
        DeviceSlot slot = context.getStore(NAMESPACE).remove(SLOT_KEY, DeviceSlot.class);
        try {
            DriverManager.close();
        } finally {
            DevicePool.get().release(slot);
        }
    }
}
