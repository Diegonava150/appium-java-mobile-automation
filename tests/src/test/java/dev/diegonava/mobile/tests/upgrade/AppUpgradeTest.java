package dev.diegonava.mobile.tests.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.AppLifecycle;
import dev.diegonava.mobile.core.junit.EnabledOnPlatform;
import dev.diegonava.mobile.core.junit.MobileUpgradeTest;
import dev.diegonava.mobile.core.upgrade.AppUpgrade;
import dev.diegonava.mobile.screens.App;
import dev.diegonava.mobile.screens.CartScreen;
import dev.diegonava.mobile.screens.CatalogScreen;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Upgrading from the previous published release to the current one, in place.
 *
 * <p>This is the scenario every user goes through and almost no suite covers. Tests that install
 * fresh every time can never see a migration bug, because a migration bug only exists for someone
 * who already had the app.
 *
 * <p>Android only: the iOS simulator has no equivalent of {@code adb install -r}, so an in-place
 * upgrade of an installed simulator build is not expressible. Recorded rather than quietly skipped.
 */
@MobileUpgradeTest
@EnabledOnPlatform(
        value = MobilePlatform.ANDROID,
        reason = "in-place upgrade needs `adb install -r`; the iOS simulator has no equivalent")
@Epic("Release")
@Feature("App upgrade")
@DisplayName("App upgrade 1.2.0 -> 1.3.0")
class AppUpgradeTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("the session starts on the previous release")
    void baselineIsThePreviousRelease() {
        assertThat(AppUpgrade.installedVersion())
                .as("the upgrade suite must begin on the older build, or it is testing nothing")
                .isEqualTo("1.2.0");

        assertThat(App.launch().isDisplayed())
                .as("the previous release should still be usable")
                .isTrue();
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("the app survives an in-place upgrade and stays usable")
    void appRemainsUsableAfterUpgrade() {
        App.launch();
        assertThat(AppUpgrade.installedVersion()).isEqualTo("1.2.0");

        String upgraded = AppUpgrade.toCurrentRelease();

        assertThat(upgraded)
                .as("the package manager should now report the new build")
                .isEqualTo("1.3.0");

        CatalogScreen catalog = App.launch();
        assertThat(catalog.isDisplayed())
                .as("the upgraded app should launch straight back into the catalog")
                .isTrue();
        assertThat(catalog.visibleItemCount())
                .as("the catalog should still be populated after the upgrade")
                .isPositive();
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("an upgrade loses no more state than an ordinary restart does")
    void upgradePreservesAsMuchStateAsARestart() {
        CatalogScreen catalog = App.launch();
        catalog.openFirstItem().addToCart();

        CartScreen cart = App.navigation().openCart();
        assertThat(cart.itemCount())
                .as("the item should be in the cart before we start")
                .isEqualTo(1);

        // Control: what does this app persist across a plain cold start on the SAME version?
        // Asserting a hard-coded expectation here would be guessing at the app's design. Measuring
        // it makes the test describe the real contract instead.
        AppLifecycle.coldRestart();
        App.launch();
        int survivingARestart = App.navigation().openCart().itemCount();

        // Now the same question across a version boundary.
        AppUpgrade.toCurrentRelease();
        App.launch();
        int survivingAnUpgrade = App.navigation().openCart().itemCount();

        assertThat(survivingAnUpgrade)
                .as(
                        "an upgrade must honour the same persistence contract as a restart: "
                                + "a restart kept %d item(s), so the upgrade must keep %d too",
                        survivingARestart, survivingARestart)
                .isEqualTo(survivingARestart);
    }
}
