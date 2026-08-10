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
