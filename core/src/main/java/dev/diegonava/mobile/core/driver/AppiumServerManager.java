package dev.diegonava.mobile.core.driver;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.device.DeviceSlot;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the Appium endpoint for a slot, optionally starting a server for it.
 *
 * <p>Default behaviour is to assume a server is already listening on the slot's port. That is what
 * CI does — Appium runs as its own workflow step, so its logs are a separate artifact and a server
 * crash is distinguishable from a test failure.
 *
 * <p>{@code -Dmobile.appium.autoStart=true} instead spawns one server per slot, which makes a local
 * run a single command. It is opt-in because locating the {@code appium} executable is
 * environment-specific, and a launcher that silently fails is worse than one you asked for.
 */
public final class AppiumServerManager {

    private static final Logger log = LoggerFactory.getLogger(AppiumServerManager.class);

    private static final Map<Integer, AppiumDriverLocalService> SERVICES = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(AppiumServerManager::stopAll, "appium-server-shutdown"));
    }

    private AppiumServerManager() {}

    public static URL endpointFor(DeviceSlot slot) {
        FrameworkConfig config = FrameworkConfig.get();
        if (config.appiumAutoStart()) {
            return startFor(slot).getUrl();
        }
        String url = "http://%s:%d/".formatted(config.appiumHost(), slot.appiumPort());
        try {
            return URI.create(url).toURL();
        } catch (Exception e) {
            throw new IllegalStateException("Malformed Appium URL: " + url, e);
        }
    }

    private static AppiumDriverLocalService startFor(DeviceSlot slot) {
        return SERVICES.computeIfAbsent(slot.appiumPort(), port -> {
            log.info("Starting a local Appium server on port {} for slot {}", port, slot.index());
            AppiumDriverLocalService service = new AppiumServiceBuilder()
                    .usingPort(port)
                    .withArgument(GeneralServerFlag.SESSION_OVERRIDE)
                    .withArgument(GeneralServerFlag.LOG_LEVEL, "info")
                    .build();
            service.start();
            if (!service.isRunning()) {
                throw new IllegalStateException(
                        "Appium server on port %d failed to start. Run it manually and drop -Dmobile.appium.autoStart."
                                .formatted(port));
            }
            return service;
        });
    }

    public static void stopAll() {
        SERVICES.values().forEach(service -> {
            if (service.isRunning()) {
                service.stop();
            }
        });
        SERVICES.clear();
    }
}
