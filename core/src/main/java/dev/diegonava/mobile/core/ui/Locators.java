package dev.diegonava.mobile.core.ui;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The single locator strategy this framework uses.
 *
 * <p>React Native's {@code testID} prop surfaces as {@code content-desc} on Android and as the
 * accessibility identifier on iOS. Appium's {@code accessibility id} strategy targets exactly
 * those two attributes. So one call resolves on both platforms, and the parity story costs a
 * single method.
 *
 * <p>Worth noting because it is a concrete, checkable claim: Sauce Labs' own reference test suite
 * for this app builds Android locators as {@code //*[@content-desc="..."]} XPath while using
 * {@code id=} on iOS. That works, but it is slower — XPath forces a full view-hierarchy walk on
 * every lookup — and it splits one concept across two syntaxes for no benefit. {@link #id} is the
 * same lookup without either cost, and {@code ./gradlew checkNoXPath} keeps it that way.
 *
 * <p>See ADR-003.
 */
public final class Locators {

    private Locators() {}

    /**
     * Locate by the app's {@code testID}. Works identically on Android and iOS.
     *
     * @param testId the value of the React Native {@code testID} prop
     */
    public static By id(String testId) {
        return AppiumBy.accessibilityId(testId);
    }
}
