package dev.diegonava.mobile.tests.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.screens.App;
import dev.diegonava.mobile.screens.CatalogScreen;
import dev.diegonava.mobile.screens.LoginScreen;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Authentication, run unchanged on Android and iOS.
 *
 * <p>There is no platform branching in this file and no {@code if (isAndroid())} anywhere in it.
 * That is the parity claim, and it is only meaningful because it is visible in the source: the
 * same class runs under {@code -Dmobile.platform=android} and {@code -Dmobile.platform=ios}.
 */
@MobileTest
@Epic("Authentication")
@Feature("Login")
@DisplayName("Login")
class LoginParityTest {

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("a valid user reaches the catalog")
    void validCredentialsReachTheCatalog() {
        App.launch();

        CatalogScreen catalog = App.navigation().openLogin().loginAsValidUser();

        assertThat(catalog.isDisplayed())
                .as("catalog should be shown after a successful login")
                .isTrue();
        assertThat(catalog.visibleItemCount())
                .as("the catalog should have products in it")
                .isPositive();
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("submitting an empty form surfaces both field errors")
    void emptyCredentialsSurfaceFieldErrors() {
        App.launch();

        LoginScreen login = App.navigation().openLogin();
        login.enterUsername("").enterPassword("").submitExpectingFailure();

        assertThat(login.hasUsernameError())
                .as("the username field should report that it is required")
                .isTrue();
        assertThat(login.hasPasswordError())
                .as("the password field should report that it is required")
                .isTrue();
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("the locked-out account is refused")
    void lockedOutUserIsRefused() {
        App.launch();

        LoginScreen login = App.navigation().openLogin();
        login.enterUsername(LoginScreen.LOCKED_OUT_USERNAME)
                .enterPassword(LoginScreen.VALID_PASSWORD)
                .submitExpectingFailure();

        assertThat(login.hasGenericError())
                .as("a locked-out account should be refused with an explanation")
                .isTrue();
        assertThat(login.genericError()).containsIgnoringCase("locked out");
    }
}
