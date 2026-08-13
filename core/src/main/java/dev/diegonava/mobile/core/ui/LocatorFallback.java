package dev.diegonava.mobile.core.ui;

import io.appium.java_client.AppiumDriver;
import java.util.Optional;
import java.util.ServiceLoader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * A last resort for a locator that did not match.
 *
 * <p>An interface in {@code core} with its implementation in {@code ai}, discovered through
 * {@link ServiceLoader}. That is what makes "the AI layer is optional" a structural fact rather
 * than a claim in a README: {@code core} does not depend on {@code ai}, and with the module absent
 * from the runtime classpath there is simply no fallback and every locator behaves exactly as it
 * always did.
 *
 * <p>The contract is deliberately narrow. A fallback may find an element; it may not decide that a
 * test passed, retry anything, or modify the page. Everything it does is recorded as debt.
 */
public interface LocatorFallback {

    /**
     * Attempts to find an element the normal strategy missed.
     *
     * @return the element if found, otherwise empty — never an exception for "not found"
     */
    Optional<WebElement> locate(AppiumDriver driver, By failedLocator, String description);

    /** Whether this fallback can actually run — credentials present, and so on. */
    boolean isAvailable();

    /**
     * The registered fallback, if the ai module is on the classpath and usable.
     *
     * <p>Resolved on every call rather than cached, so a test can disable it without a restart.
     */
    static Optional<LocatorFallback> discover() {
        return ServiceLoader.load(LocatorFallback.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(LocatorFallback::isAvailable)
                .findFirst();
    }
}
