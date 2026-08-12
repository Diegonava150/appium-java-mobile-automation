package dev.diegonava.mobile.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolved framework configuration.
 *
 * <p>Precedence, highest first: JVM system property, then environment variable (the key uppercased
 * with dots turned into underscores, so {@code mobile.platform} reads {@code MOBILE_PLATFORM}),
 * then {@code mobile.properties} on the classpath, then the built-in default.
 *
 * <p>The env-var tier is what lets CI configure a run without editing a file or threading a dozen
 * {@code -D} flags through a workflow YAML.
 */
public final class FrameworkConfig {

    private static final Properties FILE_PROPERTIES = loadClasspathProperties();
    private static final FrameworkConfig INSTANCE = new FrameworkConfig();

    private FrameworkConfig() {}

    public static FrameworkConfig get() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------- platform

    public MobilePlatform platform() {
        return MobilePlatform.parse(string("mobile.platform", "android"));
    }

    // ------------------------------------------------------------------ appium

    public String appiumHost() {
        return string("mobile.appium.host", "127.0.0.1");
    }

    /** Base port. Slot <i>n</i> uses {@code base + n}, so parallel servers never collide. */
    public int appiumBasePort() {
        return integer("mobile.appium.basePort", 4723);
    }

    /**
     * Start an Appium server per device slot instead of assuming one is already listening.
     *
     * <p>Off by default: CI starts Appium as its own step, and on Windows the service builder has
     * to guess at node/appium paths. Opt in locally with {@code -Dmobile.appium.autoStart=true}.
     */
    public boolean appiumAutoStart() {
        return bool("mobile.appium.autoStart", false);
    }

    // ------------------------------------------------------------------ devices

    public int deviceCount() {
        return Math.max(1, integer("mobile.device.count", 1));
    }

    /**
     * Explicit device identifiers, comma separated.
     *
     * <p>Empty is legitimate and common: with a single attached emulator, letting Appium pick is
     * simpler than pinning a UDID that changes between runs.
     */
    public List<String> deviceIds() {
        String key = platform().isAndroid() ? "mobile.android.devices" : "mobile.ios.devices";
        String raw = string(key, "");
        if (raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public String deviceName() {
        return platform().isAndroid()
                ? string("mobile.android.deviceName", "Android Emulator")
                : string("mobile.ios.deviceName", "iPhone 16");
    }

    public Optional<String> platformVersion() {
        String key = platform().isAndroid() ? "mobile.android.platformVersion" : "mobile.ios.platformVersion";
        return optional(key);
    }

    public int systemPortBase() {
        return integer("mobile.android.systemPortBase", 8200);
    }

    public int wdaLocalPortBase() {
        return integer("mobile.ios.wdaLocalPortBase", 8100);
    }

    public int mjpegServerPortBase() {
        return integer("mobile.mjpegServerPortBase", 9100);
    }

    /**
     * Where XCUITest keeps WebDriverAgent's build output.
     *
     * <p>Without this, WDA is rebuilt from scratch for every session. Across a 21-test suite that
     * is the single largest cost on iOS — it is most of why the simulator lane takes forty minutes
     * against Android's eight. Pointing every session at one derived-data directory means WDA is
     * compiled once and reused, and the directory is cacheable between CI runs.
     */
    public Optional<Path> iosDerivedDataPath() {
        return optional("mobile.ios.derivedDataPath").map(Path::of).map(Path::toAbsolutePath);
    }

    /**
     * Whether XCUITest should dismiss iOS system alerts automatically. On by default.
     *
     * <p>Signing in makes iOS offer to save the password, and that dialog belongs to the system,
     * not the app — it sits above the catalog and makes every row report as not visible. The
     * symptom is baffling from a log: the screen is present, fully populated, and untappable.
     *
     * <p>Turn it off for any test whose subject <i>is</i> a system dialog — a permission prompt,
     * say. Auto-dismissal would answer the prompt before the test could look at it.
     */
    public boolean iosAutoDismissAlerts() {
        return bool("mobile.ios.autoDismissAlerts", true);
    }

    // --------------------------------------------------------------------- app

    /**
     * Path to the build under test.
     *
     * <p>Absent is valid — with {@code mobile.app.package} set and the app already installed, the
     * driver attaches to it instead of reinstalling, which is much faster in a tight edit loop.
     */
    public Optional<Path> appPath() {
        String key = platform().isAndroid() ? "mobile.app.android" : "mobile.app.ios";
        return optional(key).map(Path::of).map(Path::toAbsolutePath);
    }

    /**
     * The current release APK, addressed by a key of its own.
     *
     * <p>Separate from {@link #appPath()} because the upgrade suite deliberately unsets the
     * {@code app} capability so the driver attaches rather than reinstalls — yet the test still
     * needs to know where the new binary lives in order to install it mid-session.
     */
    public Optional<Path> currentAppPath() {
        return optional("mobile.app.android.current")
                .or(() -> optional("mobile.app.android"))
                .map(Path::of)
                .map(Path::toAbsolutePath);
    }

    /**
     * The previous release of the app, used as the starting point for upgrade testing.
     *
     * <p>Downloaded by {@code ./gradlew downloadAndroidPreviousApp}. See ADR-001 for why two real
     * published builds are used rather than a synthetic version bump.
     */
    public Optional<Path> previousAppPath() {
        return optional("mobile.app.android.previous").map(Path::of).map(Path::toAbsolutePath);
    }

    /** Android package id. Optional: Appium reads it from the APK manifest when {@code app} is set. */
    public Optional<String> appPackage() {
        return optional("mobile.app.package");
    }

    public Optional<String> appActivity() {
        return optional("mobile.app.activity");
    }

    public Optional<String> bundleId() {
        return optional("mobile.app.bundleId");
    }

    public boolean noReset() {
        return bool("mobile.app.noReset", false);
    }

    public boolean fullReset() {
        return bool("mobile.app.fullReset", false);
    }

    // ---------------------------------------------------------------- timeouts

    public Duration elementTimeout() {
        return Duration.ofSeconds(integer("mobile.timeout.element.seconds", 20));
    }

    public Duration commandTimeout() {
        return Duration.ofSeconds(integer("mobile.timeout.command.seconds", 120));
    }

    public Duration deviceLeaseTimeout() {
        return Duration.ofSeconds(integer("mobile.timeout.deviceLease.seconds", 300));
    }

    // --------------------------------------------------------------- artifacts

    public Path artifactsDir() {
        return Path.of(string("mobile.artifacts.dir", "build/test-artifacts")).toAbsolutePath();
    }

    public boolean captureOnFailure() {
        return bool("mobile.artifacts.onFailure", true);
    }

    // ----------------------------------------------------------------- lookup

    public String string(String key, String fallback) {
        return optional(key).orElse(fallback);
    }

    public int integer(String key, int fallback) {
        return optional(key).map(Integer::parseInt).orElse(fallback);
    }

    public boolean bool(String key, boolean fallback) {
        return optional(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    public Optional<String> optional(String key) {
        // Thread-scoped overrides outrank everything: they are a specific test saying "not for me".
        Optional<Optional<String>> override = SessionOverrides.lookup(key);
        if (override.isPresent()) {
            return override.get();
        }
        String fromSystem = System.getProperty(key);
        if (isSet(fromSystem)) {
            return Optional.of(fromSystem.trim());
        }
        String fromEnv = System.getenv(toEnvKey(key));
        if (isSet(fromEnv)) {
            return Optional.of(fromEnv.trim());
        }
        String fromFile = FILE_PROPERTIES.getProperty(key);
        return isSet(fromFile) ? Optional.of(fromFile.trim()) : Optional.empty();
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String toEnvKey(String key) {
        return key.replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private static Properties loadClasspathProperties() {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream("mobile.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read mobile.properties from the classpath", e);
        }
        return properties;
    }
}
