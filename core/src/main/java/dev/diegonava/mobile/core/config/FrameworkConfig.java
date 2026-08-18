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
     * <p>Without this, WDA is rebuilt from scratch for every session. Pointing every session at
     * one derived-data directory means it is compiled once and reused, and the directory is
     * cacheable between CI runs.
     *
     * <p>This note used to claim the rebuild was "most of why the simulator lane takes forty
     * minutes against Android's eight". That was wrong, and wrong in the way worth recording: the
     * cache was in place, working, and the lane still took forty minutes. It was measured
     * afterwards rather than reasoned about, and the cost turned out to be per-interaction rather
     * than per-session — see {@link #iosWaitForIdleTimeout()}. Worth a real saving, just not that
     * one.
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

    /**
     * How long XCUITest waits for the app to go idle before each interaction. Zero by default,
     * which disables the wait.
     *
     * <p>This is the single biggest cost on the iOS lane, and it is not what the derived-data
     * note above claims. That cache is in place and the lane still took forty minutes, so the
     * suite was measured against Android instead — the same fourteen parity tests, 37.6 min on
     * iOS against 4.0 min on Android.
     *
     * <p>The shape of that gap is what identifies the cause. Session setup would add a fixed cost
     * per test, so short tests would show a large ratio and long ones a small one. Instead the
     * overhead scales with the test: {@code iOS ≈ 25s + 6.2 × Android} across the suite. A
     * constant of 25 s is the session; the multiplier of 6.2 is paid per interaction, and that is
     * where the thirty-odd minutes are.
     *
     * <p>Before each interaction XCUITest waits for the application to report idle. React Native
     * never does — the bridge, the animation driver and any timer keep it busy — so every tap and
     * every lookup waits out the full timeout and then proceeds anyway. The wait buys nothing
     * here and costs it repeatedly, which is exactly the 6.2×.
     *
     * <p>Raise it if a test races the UI. That would be a real signal rather than this one, and
     * the honest fix is usually an explicit wait on the thing being raced.
     */
    public Duration iosWaitForIdleTimeout() {
        return Duration.ofSeconds(integer("mobile.ios.waitForIdleTimeout.seconds", 0));
    }

    /**
     * Whether to ask the simulator to reduce motion. On by default.
     *
     * <p>Transition animations are time the suite spends waiting for pixels it never asserts on,
     * and an element mid-animation is the classic source of a tap landing on nothing. Turning
     * them off is both faster and steadier.
     */
    public boolean iosReduceMotion() {
        return bool("mobile.ios.reduceMotion", true);
    }

    /**
     * How long XCUITest waits for a simulator to finish booting.
     *
     * <p>Appium's default is 120 s, and on a shared macOS CI runner that is too tight. A run
     * failed on exactly it: <i>"The simulator … has failed to finish booting after 128 s"</i>,
     * thrown as a {@code SessionNotCreatedException} partway through the suite, after 27 of 28
     * tests had passed. The workflow pre-boots the simulator before Appium starts, so this was a
     * re-boot mid-run on a machine that was already busy, not a cold start.
     *
     * <p>Raising the ceiling does not fix a simulator that has genuinely wedged; it removes the
     * case where a slow boot is misread as a broken one. Those two look identical in a log and
     * need opposite responses, which is why the number is worth stating rather than inheriting.
     */
    public Duration iosSimulatorStartupTimeout() {
        return Duration.ofSeconds(integer("mobile.ios.simulatorStartupTimeout.seconds", 240));
    }

    /**
     * How long the HTTP client waits for a single Appium command, session creation included.
     *
     * <p>This has to be the largest number in the timeout stack, and getting that wrong is how
     * the iOS lane failed twice in a row. Selenium's {@code JdkHttpClient} defaults to a three
     * minute read timeout. Nobody set it, so nobody noticed that it sat <em>below</em> the
     * framework's own 240 s {@code wdaLaunchTimeout} — which meant that ceiling could never
     * actually be reached. Session creation on iOS ran past three minutes and the client gave up
     * first, surfacing as a bare {@code java.util.concurrent.TimeoutException} inside a
     * {@code SessionNotCreatedException} with a null message: an error that names nothing at all.
     *
     * <p>It is precisely the pathology ADR-004 describes for implicit and explicit waits, one
     * layer down. Several timeouts govern the same operation, the effective one is whichever is
     * smallest, and the smallest was the one nobody had chosen. The ladder is now deliberate and
     * ordered, longest last:
     *
     * <pre>
     *   element wait          20 s   what a test waits for a control
     *   newCommandTimeout    120 s   server-side idle-session reaper
     *   wdaLaunchTimeout     240 s   WebDriverAgent coming up
     *   simulatorStartup     240 s   a simulator booting
     *   session (this)       600 s   the client's patience — must exceed a whole cold start, not
     *                                merely the largest single step inside one
     * </pre>
     *
     * <p>It was 420 s, and 420 s was derived rather than measured: the boot ceiling plus the
     * command timeout, 240 + 120, rounded up. That sum turned out to describe only part of what
     * the first session does. From the Appium log of a failing run:
     *
     * <pre>
     *   POST /session 200   432597 ms   &lt;- the first session
     *   POST /session 200    60631 ms
     *   POST /session 200    74957 ms
     *   POST /session 200   ~30-45 s    &lt;- every one after that
     * </pre>
     *
     * <p>432 s against a 420 s ceiling. The client gave up twelve seconds before Appium finished,
     * Appium then returned {@code 200} to nobody, and the test saw
     * {@code SessionNotCreatedException} wrapping a {@code TimeoutException} with a null message —
     * the same shape of unhelpful error the 420 s was introduced to fix, one layer further out.
     * A 3% margin is also why it failed intermittently rather than every time.
     *
     * <p>A cold start is more than a boot. Simulator UI launch, roughly 39 s; installing the app,
     * roughly 70 s; then building and launching WebDriverAgent, polling {@code ECONNREFUSED} on
     * 8100 until it answers. Only the boot was in the arithmetic.
     *
     * <p>600 s is the measured 432 s with about 40% of headroom, on the reasoning that a shared
     * macOS runner is exactly the machine that will one day be slower than the one measured. The
     * cost of being generous is that a genuinely wedged session now takes ten minutes to fail
     * rather than seven, against a sixty minute job budget.
     */
    public Duration sessionTimeout() {
        return Duration.ofSeconds(integer("mobile.timeout.session.seconds", 600));
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

    // ------------------------------------------------------------- performance

    /**
     * How many cold starts to average over. One launch on shared hardware means nothing.
     */
    public int startupSamples() {
        return Math.max(1, integer("mobile.perf.coldStart.samples", 3));
    }

    /**
     * Budget for the median cold start.
     *
     * <p>The default is deliberately loose. A software-rendered emulator on a shared two-core CI
     * runner is several times slower than any real handset, so an absolute number tuned on a
     * developer's machine would fail constantly and teach everyone to ignore the gate. What this
     * catches is a regression of the order that matters — an app that suddenly takes twice as long
     * to start — while the recorded numbers accumulate into a trend that is the more useful signal.
     */
    public Duration startupBudget() {
        return Duration.ofMillis(integer("mobile.perf.coldStart.maxMillis", 4000));
    }

    /** Maximum share of janky frames tolerated. Loose for the same reason as the startup budget. */
    public double jankBudgetPercent() {
        return optional("mobile.perf.jank.maxPercent").map(Double::parseDouble).orElse(60d);
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
