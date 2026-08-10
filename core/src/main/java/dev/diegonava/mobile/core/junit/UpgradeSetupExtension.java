package dev.diegonava.mobile.core.junit;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.config.SessionOverrides;
import dev.diegonava.mobile.core.device.AdbClient;
import dev.diegonava.mobile.core.upgrade.AppUpgrade;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts the device on the previous release before the session opens.
 *
 * <p>Sequence: uninstall whatever is there, install the old build clean, then tell the driver not
 * to supply an {@code app} at all and to attach to the installed package with {@code noReset}.
 * Without unsetting {@code app}, Appium would install the current release before the test got a
 * chance to run, which is precisely the state the test exists to avoid.
 */
public final class UpgradeSetupExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(UpgradeSetupExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        FrameworkConfig config = FrameworkConfig.get();

        Path previous = config.previousAppPath()
                .orElseThrow(() -> new IllegalStateException(
                        "Upgrade tests need mobile.app.android.previous. Run ./gradlew downloadAndroidPreviousApp."));

        String appPackage = config.appPackage()
                .orElseThrow(() -> new IllegalStateException(
                        "Upgrade tests need mobile.app.package — the driver has no APK to infer it from."));

        AdbClient adb = AppUpgrade.adb();

        if (adb.isInstalled(appPackage)) {
            adb.uninstall(appPackage);
        }
        adb.installKeepingData(previous, true);
        log.info(
                "Upgrade baseline: {} version {} installed on {}",
                appPackage,
                adb.installedVersionName(appPackage),
                adb.serial());

        // Attach to what adb just installed, rather than letting Appium reinstall.
        SessionOverrides.unset("mobile.app.android");
        SessionOverrides.put("mobile.app.package", appPackage);
        SessionOverrides.put("mobile.app.activity", AppUpgrade.resolveLaunchActivity(adb, appPackage));
        SessionOverrides.put("mobile.app.noReset", "true");
        SessionOverrides.put("mobile.app.fullReset", "false");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        SessionOverrides.clear();
    }
}
