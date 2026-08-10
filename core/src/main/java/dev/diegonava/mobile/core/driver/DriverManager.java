package dev.diegonava.mobile.core.driver;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.DeviceSlot;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the driver for the current test thread.
 *
 * <p>{@code ThreadLocal} rather than a field on a base class: screen objects need the driver
 * without being handed one through three constructors, and JUnit's parallel execution gives each
 * test its own thread. The lifecycle is driven entirely by {@code DriverExtension} — nothing else
 * should call {@link #open} or {@link #close}.
 */
public final class DriverManager {

    private static final Logger log = LoggerFactory.getLogger(DriverManager.class);

    private static final ThreadLocal<AppiumDriver> DRIVER = new ThreadLocal<>();
    private static final ThreadLocal<DeviceSlot> SLOT = new ThreadLocal<>();

    private DriverManager() {}

    public static AppiumDriver driver() {
        AppiumDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException(
                    "No driver on this thread. Annotate the test class with @MobileTest so the driver "
                            + "extension is registered.");
        }
        return driver;
    }

    public static DeviceSlot slot() {
        DeviceSlot slot = SLOT.get();
        if (slot == null) {
            throw new IllegalStateException("No device slot leased on this thread.");
        }
        return slot;
    }

    public static MobilePlatform platform() {
        return FrameworkConfig.get().platform();
    }

    public static boolean isOpen() {
        return DRIVER.get() != null;
    }

    /**
     * Opens a session on the given slot.
     *
     * <p>Public only because {@code DriverExtension} lives in a sibling package. Nothing else
     * should call this — the driver lifecycle belongs to the extension, and a test that opens its
     * own session will leak both the session and the device slot.
     */
    public static void open(DeviceSlot slot) {
        if (DRIVER.get() != null) {
            throw new IllegalStateException("A driver is already open on this thread; close it first.");
        }
        MobilePlatform platform = platform();
        URL endpoint = AppiumServerManager.endpointFor(slot);

        log.info("Opening {} session on {} via {}", platform, slot.label(), endpoint);
        long startedAt = System.nanoTime();

        AppiumDriver driver = platform.isAndroid()
                ? new AndroidDriver(endpoint, CapabilityFactory.forSlot(platform, slot))
                : new IOSDriver(endpoint, CapabilityFactory.forSlot(platform, slot));

        // No implicit wait, deliberately. Implicit and explicit waits interact badly — the
        // combination produces timeouts that are neither value and are miserable to debug.
        // Every wait in this framework is explicit. See ADR-004.
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO);

        DRIVER.set(driver);
        SLOT.set(slot);

        log.info(
                "Session {} ready on {} in {}ms",
                driver.getSessionId(),
                slot.label(),
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    /** Quits the session on this thread. See {@link #open} on why this is public. */
    public static void close() {
        AppiumDriver driver = DRIVER.get();
        try {
            if (driver != null) {
                log.info("Quitting session {}", driver.getSessionId());
                driver.quit();
            }
        } catch (RuntimeException e) {
            // A failed quit must never mask the test's own failure.
            log.warn("Failed to quit the driver cleanly: {}", e.getMessage());
        } finally {
            DRIVER.remove();
            SLOT.remove();
        }
    }
}
