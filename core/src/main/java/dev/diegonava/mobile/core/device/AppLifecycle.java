package dev.diegonava.mobile.core.device;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.driver.DriverManager;
import io.appium.java_client.InteractsWithApps;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * App process lifecycle: the states a mobile app has and a web page does not.
 *
 * <p>Cold start, warm start, backgrounding and process death are where a large share of real
 * mobile bugs live, because they are the moments an app has to serialise and restore its own
 * state. A suite that only ever drives a freshly launched app never visits any of them.
 */
public final class AppLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AppLifecycle.class);

    private AppLifecycle() {}

    private static InteractsWithApps apps() {
        return (InteractsWithApps) DriverManager.driver();
    }

    private static String appId() {
        FrameworkConfig config = FrameworkConfig.get();
        return config.platform().isAndroid()
                ? config.appPackage().orElseThrow(() -> new IllegalStateException("mobile.app.package is not set"))
                : config.bundleId().orElseThrow(() -> new IllegalStateException("mobile.app.bundleId is not set"));
    }

    /** Kills the app process outright. The next launch is a cold start. */
    public static void terminate() {
        String id = appId();
        apps().terminateApp(id);
        log.info("Terminated {}", id);
    }

    /** Brings the app to the foreground, launching it if it is not running. */
    public static void activate() {
        String id = appId();
        apps().activateApp(id);
        log.info("Activated {}", id);
    }

    /**
     * A full cold restart: kill the process, then launch it again.
     *
     * <p>Used as the control in upgrade testing. Whatever state survives this is the app's own
     * persistence contract, and an upgrade is expected to honour exactly the same contract.
     */
    public static void coldRestart() {
        terminate();
        activate();
    }

    /**
     * Sends the app to the background for a while, then brings it back.
     *
     * <p>A negative duration would background it permanently, which is not what any test wants.
     */
    public static void background(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Background duration must be positive, was " + duration);
        }
        log.info("Backgrounding the app for {}", duration);
        apps().runAppInBackground(duration);
    }

    /**
     * Puts the app back to the state a freshly created session would find it in.
     *
     * <p>Removes and reinstalls it, which is what Appium's own between-session reset does while
     * {@code noReset} and {@code fullReset} are both false. That is the point: this exists so a
     * session can be reused across a class without the tests inside it becoming order-dependent,
     * and it is only honest to reuse a session if what replaces the reset is equivalent to it.
     *
     * <p>{@link #coldRestart()} is deliberately not enough here. Its whole purpose is to preserve
     * what the app persists — that is the contract the upgrade suite measures against — so a class
     * resetting with it would carry a signed-in user or a filled cart into the following test.
     *
     * <p>Only the app is reinstalled. Device state around it — granted permissions, orientation,
     * clipboard, anything a test simulated — survives, and a class that cannot tolerate that
     * belongs on {@code SessionScope.PER_TEST}.
     */
    public static void resetToCleanState() {
        FrameworkConfig config = FrameworkConfig.get();
        String id = appId();
        String app = config.appPath()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot reset " + id + " to a clean state: mobile.app.path is not set, so there is "
                                + "nothing to reinstall. A suite that unsets it — the upgrade suite does, "
                                + "deliberately — must use SessionScope.PER_TEST."))
                .toString();

        long startedAt = System.nanoTime();
        InteractsWithApps apps = apps();
        apps.terminateApp(id);
        apps.removeApp(id);
        apps.installApp(app);
        apps.activateApp(id);
        log.info("Reset {} to a clean state in {}ms", id, (System.nanoTime() - startedAt) / 1_000_000);
    }

    public static boolean isRunningInForeground() {
        return apps().queryAppState(appId()).name().equals("RUNNING_IN_FOREGROUND");
    }
}
