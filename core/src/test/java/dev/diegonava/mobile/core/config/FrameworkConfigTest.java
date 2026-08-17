package dev.diegonava.mobile.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class FrameworkConfigTest {

    @AfterEach
    void clearOverrides() {
        System.clearProperty("mobile.platform");
        System.clearProperty("mobile.timeout.element.seconds");
        System.clearProperty("mobile.app.noReset");
    }

    @Test
    @DisplayName("a system property overrides the built-in default")
    void systemPropertyWins() {
        System.setProperty("mobile.platform", "ios");
        assertThat(FrameworkConfig.get().platform()).isEqualTo(MobilePlatform.IOS);
    }

    @Test
    @DisplayName("defaults apply when nothing is configured")
    void defaultsApply() {
        assertThat(FrameworkConfig.get().platform()).isEqualTo(MobilePlatform.ANDROID);
        assertThat(FrameworkConfig.get().elementTimeout().toSeconds()).isEqualTo(20);
        assertThat(FrameworkConfig.get().deviceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the timeout stack is ordered — the client outlasts every ceiling beneath it")
    void timeoutStackIsOrdered() {
        // This is the assertion that would have caught the bug. The iOS lane failed twice because
        // Selenium's unset default read timeout (180s) sat *below* wdaLaunchTimeout (240s), so
        // that ceiling could never be reached and the client gave up first with an error that
        // named nothing. Several timeouts govern one operation and the smallest wins — which is
        // ADR-004's argument about implicit waits, one layer down.
        FrameworkConfig config = FrameworkConfig.get();

        assertThat(config.sessionTimeout())
                .as("the client must outlast a simulator boot followed by a WDA launch")
                .isGreaterThan(config.iosSimulatorStartupTimeout().plus(config.commandTimeout()));

        assertThat(config.iosSimulatorStartupTimeout().toSeconds())
                .as("Appium's own default is 120s, and a run failed at 128s")
                .isGreaterThan(120);

        assertThat(config.elementTimeout())
                .as("an element wait is the shortest thing in the stack, by a wide margin")
                .isLessThan(config.commandTimeout());
    }

    /**
     * The observed cost of the first iOS session on a GitHub macOS runner, from the Appium log of
     * the run that failed on it: {@code POST /session 200 432597 ms}.
     *
     * <p>A constant rather than a comment because the assertion below is only meaningful next to
     * the number it came from.
     */
    private static final Duration MEASURED_COLD_START = Duration.ofSeconds(433);

    @Test
    @DisplayName("the client outlasts a measured cold start, not just the arithmetic of the ladder")
    void sessionTimeoutCoversAMeasuredColdStart() {
        // The ladder above is derived: boot ceiling plus command timeout, 240 + 120, rounded to
        // 420. It described only part of what the first session does — simulator UI launch, then
        // installing the app, then building and launching WebDriverAgent. The whole thing measured
        // 432.6 s, the client gave up at 420 s, and Appium returned 200 to nobody twelve seconds
        // later. The test saw SessionNotCreatedException wrapping a null-message
        // TimeoutException: the same unhelpful shape the 420 s existed to prevent.
        //
        // Deriving a bound from other bounds is what went wrong, so this asserts against the
        // measurement instead. Anyone tightening the timeout has to argue with a real number.
        assertThat(FrameworkConfig.get().sessionTimeout())
                .as(
                        "a cold start was measured at %s; a ceiling below it fails intermittently and "
                                + "reports nothing useful when it does",
                        MEASURED_COLD_START)
                .isGreaterThan(MEASURED_COLD_START);

        assertThat(FrameworkConfig.get().sessionTimeout())
                .as("and with enough headroom that a runner slower than the one measured still "
                        + "fits — 3%% of margin is what made this intermittent rather than broken")
                .isGreaterThan(MEASURED_COLD_START.plus(Duration.ofSeconds(120)));
    }

    @Test
    @DisplayName("both new timeouts are configurable like everything else")
    void newTimeoutsAreConfigurable() {
        System.setProperty("mobile.ios.simulatorStartupTimeout.seconds", "45");
        System.setProperty("mobile.timeout.session.seconds", "99");
        try {
            assertThat(FrameworkConfig.get().iosSimulatorStartupTimeout().toSeconds())
                    .isEqualTo(45);
            assertThat(FrameworkConfig.get().sessionTimeout().toSeconds()).isEqualTo(99);
        } finally {
            System.clearProperty("mobile.ios.simulatorStartupTimeout.seconds");
            System.clearProperty("mobile.timeout.session.seconds");
        }
    }

    @Test
    @DisplayName("the idle wait is off by default, and can be turned back on")
    void idleWaitIsOffByDefault() {
        // Not a style preference. XCUITest waits for the app to report idle before every
        // interaction, and React Native never reports idle, so the wait expires and the
        // interaction proceeds anyway — bought nothing, paid per interaction. Measured across the
        // same fourteen parity tests: 37.6 min on iOS against 4.0 min on Android, with the
        // overhead scaling as the test does rather than as a fixed cost per session.
        //
        // Zero here is therefore the deliberate value, and a non-zero default arriving by
        // accident would quietly restore the 6.2x. Hence a test on the default itself.
        assertThat(FrameworkConfig.get().iosWaitForIdleTimeout())
                .as("a non-zero idle wait costs roughly six times the runtime and buys nothing "
                        + "on a React Native app")
                .isZero();

        System.setProperty("mobile.ios.waitForIdleTimeout.seconds", "5");
        try {
            assertThat(FrameworkConfig.get().iosWaitForIdleTimeout().toSeconds())
                    .as("a test that genuinely races the UI must be able to ask for the wait back")
                    .isEqualTo(5);
        } finally {
            System.clearProperty("mobile.ios.waitForIdleTimeout.seconds");
        }
    }

    @Test
    @DisplayName("numeric and boolean values are coerced from their string form")
    void coercesTypes() {
        System.setProperty("mobile.timeout.element.seconds", "45");
        System.setProperty("mobile.app.noReset", "true");

        assertThat(FrameworkConfig.get().elementTimeout().toSeconds()).isEqualTo(45);
        assertThat(FrameworkConfig.get().noReset()).isTrue();
    }

    @Test
    @DisplayName("platform parsing accepts either casing and rejects nonsense")
    void platformParsingIsForgivingButStrict() {
        assertThat(MobilePlatform.parse("ANDROID")).isEqualTo(MobilePlatform.ANDROID);
        assertThat(MobilePlatform.parse(" iOS ")).isEqualTo(MobilePlatform.IOS);
        assertThat(MobilePlatform.ANDROID.isAndroid()).isTrue();
        assertThat(MobilePlatform.IOS.isIos()).isTrue();
    }

    @Test
    @DisplayName("the device count never drops below one, whatever is configured")
    void deviceCountHasAFloor() {
        System.setProperty("mobile.device.count", "0");
        try {
            assertThat(FrameworkConfig.get().deviceCount()).isEqualTo(1);
        } finally {
            System.clearProperty("mobile.device.count");
        }
    }
}
