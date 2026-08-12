package dev.diegonava.mobile.core.driver;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.DeviceSlot;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.options.XCUITestOptions;
import java.time.Duration;
import org.openqa.selenium.Capabilities;

/** Builds the per-slot capability set for each platform. */
public final class CapabilityFactory {

    private CapabilityFactory() {}

    public static Capabilities forSlot(MobilePlatform platform, DeviceSlot slot) {
        FrameworkConfig config = FrameworkConfig.get();
        return platform.isAndroid() ? android(config, slot) : ios(config, slot);
    }

    private static UiAutomator2Options android(FrameworkConfig config, DeviceSlot slot) {
        UiAutomator2Options options = new UiAutomator2Options()
                .setPlatformName("Android")
                .setAutomationName("UiAutomator2")
                .setDeviceName(slot.deviceName())
                // Per-slot. See the DeviceSlot javadoc for why this is not optional.
                .setSystemPort(slot.systemPort())
                .setMjpegServerPort(slot.mjpegServerPort())
                .setNewCommandTimeout(config.commandTimeout())
                .setAdbExecTimeout(Duration.ofSeconds(60))
                .setUiautomator2ServerInstallTimeout(Duration.ofSeconds(120))
                // Grant runtime permissions up front. Tests that exercise the permission
                // dialogs themselves override this — see the week-2 device-conditions suite.
                .setAutoGrantPermissions(true)
                .setDisableWindowAnimation(true);

        slot.udid().ifPresent(options::setUdid);
        slot.platformVersion().ifPresent(options::setPlatformVersion);
        config.appPath().ifPresent(path -> options.setApp(path.toString()));
        config.appPackage().ifPresent(options::setAppPackage);
        config.appActivity().ifPresent(options::setAppActivity);

        options.setNoReset(config.noReset());
        options.setFullReset(config.fullReset());
        return options;
    }

    private static XCUITestOptions ios(FrameworkConfig config, DeviceSlot slot) {
        XCUITestOptions options = new XCUITestOptions()
                .setPlatformName("iOS")
                .setAutomationName("XCUITest")
                .setDeviceName(slot.deviceName())
                // Per-slot. Two simulators sharing one WDA port is the iOS-side
                // equivalent of the systemPort collision.
                .setWdaLocalPort(slot.wdaLocalPort())
                .setMjpegServerPort(slot.mjpegServerPort())
                .setNewCommandTimeout(config.commandTimeout())
                .setWdaLaunchTimeout(Duration.ofSeconds(240))
                .setWdaConnectionTimeout(Duration.ofSeconds(240))
                // Reuse the existing WDA rather than reinstalling it per session.
                .setUsePrebuiltWda(false)
                .setUseNewWDA(false);

        // Share one derived-data directory so WebDriverAgent is compiled once per machine
        // instead of once per session. This is the difference between a forty minute iOS run
        // and something comparable to Android's.
        config.iosDerivedDataPath().ifPresent(path -> options.setDerivedDataPath(path.toString()));

        slot.udid().ifPresent(options::setUdid);
        slot.platformVersion().ifPresent(options::setPlatformVersion);
        config.appPath().ifPresent(path -> options.setApp(path.toString()));
        config.bundleId().ifPresent(options::setBundleId);

        options.setNoReset(config.noReset());
        options.setFullReset(config.fullReset());
        return options;
    }
}
