package dev.diegonava.mobile.tests.conditions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.AdbClient;
import dev.diegonava.mobile.core.device.AppLifecycle;
import dev.diegonava.mobile.core.device.Display;
import dev.diegonava.mobile.core.driver.DriverManager;
import dev.diegonava.mobile.core.junit.EnabledOnPlatform;
import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.screens.App;
import dev.diegonava.mobile.screens.CatalogScreen;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.ScreenOrientation;

/**
 * Display configuration: orientation, dark theme, and larger system text.
 *
 * <p>These cover a bug class that functional tests at default settings structurally cannot see. A
 * layout only ever checked at 1.0 font scale in light theme will clip, overlap, or push its
 * primary action off screen for the many real users who do not run it that way.
 *
 * <p>The orientation test is not the one originally written. The intent was the classic Android
 * state-loss scenario — rotate mid-flow, watch the Activity get recreated, check what the app
 * forgot to save. It failed with "Screen rotation cannot be changed to ROTATION_270", and the
 * manifest says why: {@code android:screenOrientation=1}, portrait, hard-locked. Forcing
 * {@code user_rotation} through adb does not move it either.
 *
 * <p>So the scenario genuinely does not apply to this app, and the test became the assertion that
 * actually holds: the app is portrait-locked, and stays that way. A lock that silently disappears
 * in a future release would ship a rotation path nobody has ever tested, which is worth catching.
 */
@MobileTest
@Epic("Device conditions")
@DisplayName("Display conditions")
class DisplayConditionsTest {

    @Test
    @Feature("Orientation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("the app is portrait-locked and refuses to rotate")
    void appIsPortraitLocked() {
        CatalogScreen catalog = App.launch();

        assertThat(Display.orientation())
                .as("the app declares android:screenOrientation=portrait")
                .isEqualTo(ScreenOrientation.PORTRAIT);

        assertThat(Display.tryRotateTo(ScreenOrientation.LANDSCAPE))
                .as("a portrait-locked app should refuse the rotation rather than honour it")
                .isFalse();

        assertThat(Display.orientation()).isEqualTo(ScreenOrientation.PORTRAIT);
        assertThat(catalog.visibleItemCount())
                .as("a refused rotation should leave the app untouched")
                .isPositive();
    }

    @Test
    @Feature("Accessibility")
    @Severity(SeverityLevel.NORMAL)
    @EnabledOnPlatform(
            value = MobilePlatform.ANDROID,
            reason = "font scale is an Android system setting; iOS uses Dynamic Type via simctl")
    @DisplayName("the catalog stays usable at 1.3x system font scale")
    void catalogSurvivesLargeText() {
        AdbClient adb = AdbClient.forSlot(DriverManager.slot());
        try {
            adb.setFontScale(1.3d);
            // font_scale is a global configuration change: the system tears down and recreates
            // activities as it propagates. Without an explicit restart the next lookup races that
            // teardown and times out against an app that is momentarily gone.
            AppLifecycle.coldRestart();

            CatalogScreen catalog = App.launch();
            assertThat(catalog.visibleItemCount())
                    .as("larger text must not stop products rendering")
                    .isPositive();

            // The real risk is the primary action being pushed out of reach.
            catalog.openFirstItem().addToCart();
            assertThat(App.navigation().openCart().itemCount())
                    .as("add-to-cart must stay reachable at a larger font scale")
                    .isEqualTo(1);
        } finally {
            adb.setFontScale(1.0d);
        }
    }

    @Test
    @Feature("Accessibility")
    @Severity(SeverityLevel.NORMAL)
    @EnabledOnPlatform(value = MobilePlatform.ANDROID, reason = "dark theme is toggled through adb uimode")
    @DisplayName("the catalog stays usable in dark theme")
    void catalogSurvivesDarkMode() {
        AdbClient adb = AdbClient.forSlot(DriverManager.slot());
        try {
            adb.setDarkMode(true);

            CatalogScreen catalog = App.launch();
            assertThat(catalog.visibleItemCount())
                    .as("dark theme must not stop products rendering")
                    .isPositive();
            assertThat(App.navigation().isCartAffordanceVisible())
                    .as("navigation chrome must survive the theme switch")
                    .isTrue();
        } finally {
            adb.setDarkMode(false);
        }
    }
}
