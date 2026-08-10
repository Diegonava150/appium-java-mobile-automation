package dev.diegonava.mobile.core.upgrade;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.device.AdbClient;
import dev.diegonava.mobile.core.driver.DriverManager;
import io.appium.java_client.InteractsWithApps;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs an in-place app upgrade against a live session.
 *
 * <p>This is the operation almost nobody automates, and the reason is that it does not fit the
 * shape of a normal test: the binary changes while the session is running. A driver has no command
 * for "become a different version of yourself", so the upgrade goes through adb and the session is
 * then pointed back at the newly installed package.
 *
 * <p>What it proves is the thing users actually experience and teams rarely check — that data
 * written by the old version survives the update. Migration bugs are silent, they only appear for
 * existing users, and they never show up in a suite that installs fresh every time.
 */
public final class AppUpgrade {

    private static final Logger log = LoggerFactory.getLogger(AppUpgrade.class);

    private AppUpgrade() {}

    /** An adb client for the device this run is using. */
    public static AdbClient adb() {
        List<String> ids = FrameworkConfig.get().deviceIds();
        return ids.isEmpty() ? AdbClient.forSoleAttachedDevice() : AdbClient.forSerial(ids.get(0));
    }

    /**
     * Installs the current release over the running one and brings it back to the foreground.
     *
     * @return the {@code versionName} the package manager reports after the upgrade
     */
    public static String toCurrentRelease() {
        FrameworkConfig config = FrameworkConfig.get();
        Path current = config.currentAppPath()
                .orElseThrow(() ->
                        new IllegalStateException("No current APK configured. Run ./gradlew downloadAndroidApp."));

        String appPackage = config.appPackage().orElseThrow();
        AdbClient adb = adb();

        String before = adb.installedVersionName(appPackage);
        log.info("Upgrading {} from {} …", appPackage, before);

        // -r keeps app data. That is the entire scenario: without it this is a reinstall,
        // and a reinstall cannot tell you anything about migration.
        adb.installKeepingData(current, false);

        String after = adb.installedVersionName(appPackage);
        if (after.equals(before)) {
            throw new IllegalStateException(
                    "Upgrade did not change the installed version (still %s). Are both APKs the same build?"
                            .formatted(after));
        }

        ((InteractsWithApps) DriverManager.driver()).activateApp(appPackage);
        log.info("Upgraded {} from {} to {} and reactivated it", appPackage, before, after);
        return after;
    }

    /** The version currently installed, for asserting the baseline before an upgrade. */
    public static String installedVersion() {
        return adb().installedVersionName(FrameworkConfig.get().appPackage().orElseThrow());
    }

    /**
     * Resolves the launchable activity for a package.
     *
     * <p>Needed because the upgrade session deliberately has no {@code app} capability, so Appium
     * cannot read the manifest to work it out for itself.
     */
    public static String resolveLaunchActivity(AdbClient adb, String appPackage) {
        String output = adb.run("shell", "cmd", "package", "resolve-activity", "--brief", appPackage);
        return output.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(appPackage + "/"))
                .map(line -> line.substring(line.indexOf('/') + 1))
                .findFirst()
                .orElse(".MainActivity");
    }

    /** How long to allow a freshly upgraded app to come back up. */
    public static Duration relaunchTimeout() {
        return Duration.ofSeconds(30);
    }
}
