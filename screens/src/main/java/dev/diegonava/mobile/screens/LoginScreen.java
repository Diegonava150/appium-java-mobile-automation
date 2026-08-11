package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import java.time.Duration;
import org.openqa.selenium.By;

/**
 * The login screen of the Sauce Labs "My Demo App" React Native build.
 *
 * <p>Every locator below is the app's own {@code testID}, resolved through the accessibility-id
 * strategy on both platforms. No platform branching is needed anywhere on this screen.
 */
public final class LoginScreen extends BaseScreen {

    /** Standard user. Documented by the app itself on its login screen. */
    public static final String VALID_USERNAME = "bob@example.com";

    public static final String VALID_PASSWORD = "10203040";

    /** The app ships a deliberately locked-out account, which is useful for negative paths. */
    public static final String LOCKED_OUT_USERNAME = "alice@example.com";

    private static final By ROOT = id("login screen");
    private static final By USERNAME = id("Username input field");
    private static final By PASSWORD = id("Password input field");
    private static final By SUBMIT = id("Login button");
    private static final By BIOMETRICS = id("biometrics-button");
    private static final By USERNAME_ERROR = id("Username-error-message");
    private static final By PASSWORD_ERROR = id("Password-error-message");
    private static final By GENERIC_ERROR = id("generic-error-message");

    @Override
    protected By rootLocator() {
        return ROOT;
    }

    public LoginScreen enterUsername(String username) {
        type(USERNAME, username);
        return this;
    }

    public LoginScreen enterPassword(String password) {
        type(PASSWORD, password);
        return this;
    }

    /**
     * Submits and expects to land on the catalog. Use {@link #submitExpectingFailure} otherwise.
     *
     * <p>Scrolls to the button first. On Android the keyboard is dismissed after each field and
     * the button is already in view, so this is a no-op there — but the first iOS simulator run
     * failed here with "Login button ... was not visible", because XCUITest does not reliably
     * dismiss the keyboard and the button ends up underneath it.
     */
    public CatalogScreen submit() {
        scrollAndTap(SUBMIT);
        CatalogScreen catalog = new CatalogScreen();
        catalog.awaitLoaded();
        return catalog;
    }

    /** Submits and stays put, so the caller can assert on the error messaging. */
    public LoginScreen submitExpectingFailure() {
        scrollAndTap(SUBMIT);
        return this;
    }

    public CatalogScreen loginAs(String username, String password) {
        return enterUsername(username).enterPassword(password).submit();
    }

    public CatalogScreen loginAsValidUser() {
        return loginAs(VALID_USERNAME, VALID_PASSWORD);
    }

    public boolean isBiometricsButtonDisplayed() {
        return isDisplayed(BIOMETRICS, Duration.ofSeconds(5));
    }

    public void tapBiometrics() {
        tap(BIOMETRICS);
    }

    public boolean hasUsernameError() {
        return isDisplayed(USERNAME_ERROR, Duration.ofSeconds(5));
    }

    public boolean hasPasswordError() {
        return isDisplayed(PASSWORD_ERROR, Duration.ofSeconds(5));
    }

    public String usernameError() {
        return textOf(USERNAME_ERROR);
    }

    public String genericError() {
        return textOf(GENERIC_ERROR);
    }

    public boolean hasGenericError() {
        return isDisplayed(GENERIC_ERROR, Duration.ofSeconds(5));
    }
}
