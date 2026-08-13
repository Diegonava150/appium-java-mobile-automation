package dev.diegonava.mobile.tests.visual;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.AdbClient;
import dev.diegonava.mobile.core.driver.DriverManager;
import dev.diegonava.mobile.core.junit.EnabledOnPlatform;
import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.core.visual.ImageComparison;
import dev.diegonava.mobile.core.visual.VisualRegression;
import dev.diegonava.mobile.screens.App;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;

/**
 * Visual regression against per-device baselines.
 *
 * <p>Per device is the whole design. A screenshot is a function of resolution, density, cutout
 * shape and OS version, so one golden image cannot serve a fleet — which is the main reason mobile
 * visual testing is harder than the web equivalent, and the reason a size mismatch here is an
 * explicit error rather than a 100% difference score.
 *
 * <p>Baselines are recorded on first run rather than failing, so adding a new screen or a new
 * device profile is not a red build. Rewrite them deliberately with
 * {@code -Dmobile.visual.update=true} after an intended UI change, and review the diff images the
 * failure path writes.
 */
@MobileTest
@EnabledOnPlatform(value = MobilePlatform.ANDROID, reason = "device profile is resolved through adb")
@Epic("Visual")
@Feature("Screenshot comparison")
@DisplayName("Visual regression")
class VisualRegressionTest {

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("the catalog matches its baseline for this device profile")
    void catalogMatchesBaseline() {
        App.launch();
        assertMatchesBaseline("catalog");
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("the login screen matches its baseline for this device profile")
    void loginMatchesBaseline() {
        App.launch();
        App.navigation().openLogin();
        assertMatchesBaseline("login");
    }

    private void assertMatchesBaseline(String name) {
        AdbClient adb = AdbClient.forSlot(DriverManager.slot());
        Dimension size = DriverManager.driver().manage().window().getSize();
        int density = adb.displayDensityDpi();

        String profile = VisualRegression.profileOf(
                "android",
                adb.run("shell", "getprop", "ro.build.version.sdk").strip(),
                density,
                size.getWidth(),
                size.getHeight());

        // The status bar carries a clock and a battery indicator, both of which change between any
        // two runs. Leaving it in scope means every comparison fails on the time.
        int statusBarHeightPx = Math.round(24f * density / 160f);

        VisualRegression visual = VisualRegression.forProfile(profile, statusBarHeightPx, size.getWidth());
        Optional<ImageComparison> result = visual.check(name);

        if (result.isEmpty()) {
            Allure.addAttachment(
                    "visual baseline (" + name + ")",
                    "text/plain",
                    "Recorded a baseline for profile '" + profile + "'. No comparison was made this run.");
            return;
        }

        ImageComparison comparison = result.get();
        Allure.addAttachment(
                "visual comparison (" + name + ")",
                "text/plain",
                comparison + System.lineSeparator()
                        + "profile: " + profile + System.lineSeparator()
                        + "tolerance: " + visual.tolerancePercentage() + "%");

        assertThat(comparison.matches(visual.tolerancePercentage()))
                .as(
                        "%s differs from its baseline by more than %.2f%% on profile %s — %s."
                                + " Baseline, actual and diff images are in the test artifacts.",
                        name, visual.tolerancePercentage(), profile, comparison)
                .isTrue();
    }
}
