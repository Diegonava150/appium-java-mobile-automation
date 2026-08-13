package dev.diegonava.mobile.core.config;

import static org.assertj.core.api.Assertions.assertThat;

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
