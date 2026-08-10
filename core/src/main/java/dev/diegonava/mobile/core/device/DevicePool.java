package dev.diegonava.mobile.core.device;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A fixed set of device slots leased to test threads.
 *
 * <p>Test parallelism and device count are two different numbers, and conflating them is how
 * mobile suites end up flaky. JUnit is told to run <i>n</i> threads where <i>n</i> is the pool
 * size, but that is an optimisation, not a guarantee — this pool is the actual gate. A thread that
 * cannot get a slot blocks until one frees up rather than racing onto a busy device.
 *
 * <p>Leases are strictly balanced: every {@link #lease()} has a matching {@link #release} in a
 * {@code finally} block inside the JUnit extension, so a test that throws still returns its device.
 */
public final class DevicePool {

    private static final Logger log = LoggerFactory.getLogger(DevicePool.class);

    private static volatile DevicePool instance;

    private final BlockingQueue<DeviceSlot> available;
    private final int size;

    private DevicePool(List<DeviceSlot> slots) {
        this.available = new LinkedBlockingQueue<>(slots);
        this.size = slots.size();
        log.info("Device pool initialised with {} slot(s)", size);
        slots.forEach(slot -> log.info(
                "  slot {} -> appium:{} system:{} wda:{} mjpeg:{} device:{}",
                slot.index(),
                slot.appiumPort(),
                slot.systemPort(),
                slot.wdaLocalPort(),
                slot.mjpegServerPort(),
                slot.udid().orElse(slot.deviceName())));
    }

    public static DevicePool get() {
        DevicePool local = instance;
        if (local == null) {
            synchronized (DevicePool.class) {
                local = instance;
                if (local == null) {
                    local = new DevicePool(buildSlots(FrameworkConfig.get()));
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Test-only hook so a pool built from different config can be swapped in. */
    static synchronized void reset() {
        instance = null;
    }

    static List<DeviceSlot> buildSlots(FrameworkConfig config) {
        List<String> ids = config.deviceIds();
        int count = ids.isEmpty() ? config.deviceCount() : ids.size();

        if (!ids.isEmpty() && ids.size() != config.deviceCount()) {
            log.info(
                    "mobile.device.count={} but {} device id(s) were listed; using the id list.",
                    config.deviceCount(),
                    ids.size());
        }

        List<DeviceSlot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots.add(new DeviceSlot(
                    i,
                    i < ids.size() ? Optional.of(ids.get(i)) : Optional.empty(),
                    config.deviceName(),
                    config.platformVersion(),
                    config.appiumBasePort() + i,
                    config.systemPortBase() + i,
                    config.wdaLocalPortBase() + i,
                    config.mjpegServerPortBase() + i));
        }
        return List.copyOf(slots);
    }

    public int size() {
        return size;
    }

    /** Blocks until a slot is free, or fails once the configured lease timeout elapses. */
    public DeviceSlot lease() {
        long timeoutSeconds = FrameworkConfig.get().deviceLeaseTimeout().toSeconds();
        try {
            DeviceSlot slot = available.poll(timeoutSeconds, TimeUnit.SECONDS);
            if (slot == null) {
                throw new IllegalStateException(
                        "No device slot became available within %ds. Pool size is %d.".formatted(timeoutSeconds, size));
            }
            log.debug("Leased {}", slot.label());
            return slot;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a device slot", e);
        }
    }

    public void release(DeviceSlot slot) {
        if (slot == null) {
            return;
        }
        log.debug("Released {}", slot.label());
        available.add(slot);
    }
}
