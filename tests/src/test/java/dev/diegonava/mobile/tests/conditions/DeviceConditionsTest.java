package dev.diegonava.mobile.tests.conditions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.AdbClient;
import dev.diegonava.mobile.core.device.AppLifecycle;
import dev.diegonava.mobile.core.driver.DriverManager;
import dev.diegonava.mobile.core.junit.EnabledOnPlatform;
import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.screens.App;
import dev.diegonava.mobile.screens.CartScreen;
import dev.diegonava.mobile.screens.CatalogScreen;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The conditions a phone is in that a browser tab never is.
 *
 * <p>An app gets backgrounded mid-checkout, killed by the OS to reclaim memory, and carried into a
 * lift with no signal. Each of those is a state transition the app has to survive, and none of them
 * has a web equivalent. A suite that only drives a freshly launched app on good wifi has not tested
 * the environment the app actually ships into.
 */
@MobileTest
@Epic("Device conditions")
@DisplayName("Device conditions")
class DeviceConditionsTest {

    @Test
    @Feature("Lifecycle")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("backgrounding mid-flow does not lose the cart")
    void backgroundingPreservesTheCart() {
        CatalogScreen catalog = App.launch();
        catalog.openFirstItem().addToCart();

        assertThat(App.navigation().openCart().itemCount()).isEqualTo(1);

        AppLifecycle.background(Duration.ofSeconds(3));

        CartScreen cart = App.navigation().openCart();
        assertThat(cart.itemCount())
                .as("a three second trip to the home screen must not empty the cart")
                .isEqualTo(1);
    }

    @Test
    @Feature("Lifecycle")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("the app is usable again after a cold start")
    void coldStartLeavesTheAppUsable() {
        App.launch();

        AppLifecycle.coldRestart();

        CatalogScreen catalog = App.launch();
        assertThat(catalog.isDisplayed()).isTrue();
        assertThat(catalog.visibleItemCount())
                .as("the catalog should repopulate after the process was killed")
                .isPositive();
        assertThat(AppLifecycle.isRunningInForeground()).isTrue();
    }

    @Test
    @Feature("Lifecycle")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("a warm start returns to a working app")
    void warmStartReturnsToAWorkingApp() {
        App.launch();

        AppLifecycle.background(Duration.ofSeconds(2));
        AppLifecycle.activate();

        assertThat(App.launch().isDisplayed())
                .as("bringing the app back from the background should not need a relaunch")
                .isTrue();
    }

    @Test
    @Feature("Connectivity")
    @Severity(SeverityLevel.CRITICAL)
    @EnabledOnPlatform(
            value = MobilePlatform.ANDROID,
            reason = "radio toggles go through adb; the iOS simulator has no radios to toggle")
    @DisplayName("the catalog still works with every radio off")
    void catalogSurvivesGoingOffline() {
        AdbClient adb = AdbClient.forSlot(DriverManager.slot());
        try {
            App.launch();

            adb.setAirplaneMode(true);

            // The catalog is bundled with the app rather than fetched, so going offline must not
            // break it. Asserting that is what would catch a regression that moved it behind a
            // network call — the kind of change that looks harmless in review.
            CatalogScreen catalog = App.launch();
            assertThat(catalog.visibleItemCount())
                    .as("bundled catalog content should not depend on connectivity")
                    .isPositive();

            catalog.openFirstItem().addToCart();
            assertThat(App.navigation().openCart().itemCount())
                    .as("adding to a local cart should work offline")
                    .isEqualTo(1);
        } finally {
            // Always restore the radios. Leaving an emulator in airplane mode would break every
            // test that ran after this one, for reasons none of them could explain.
            adb.setAirplaneMode(false);
        }
    }

    @Test
    @Feature("Connectivity")
    @Severity(SeverityLevel.NORMAL)
    @EnabledOnPlatform(value = MobilePlatform.ANDROID, reason = "radio toggles go through adb")
    @DisplayName("connectivity coming back does not disturb the app")
    void appRecoversWhenConnectivityReturns() {
        AdbClient adb = AdbClient.forSlot(DriverManager.slot());
        try {
            App.launch();
            adb.setAirplaneMode(true);
            App.launch();

            adb.setAirplaneMode(false);

            CatalogScreen catalog = App.launch();
            assertThat(catalog.isDisplayed())
                    .as("the app should still be healthy once the radios come back")
                    .isTrue();
            assertThat(catalog.visibleItemCount()).isPositive();
        } finally {
            adb.setAirplaneMode(false);
        }
    }
}
