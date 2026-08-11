package dev.diegonava.mobile.core.ui;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.driver.DriverManager;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Shared behaviour for screen objects.
 *
 * <p>Every wait here is explicit. There is no implicit wait configured on the driver, and mixing
 * the two is one of the most reliable ways to produce a suite whose timeouts make no sense — the
 * effective wait becomes some undocumented interaction of both values, varying by command. See
 * ADR-004.
 *
 * <p>Screen objects expose intent ({@code login}, {@code openFirstItem}) and return the next
 * screen. They never assert; assertions belong in tests, where the failure message can describe
 * what the test wanted rather than what the page did.
 */
public abstract class BaseScreen {

    /** Enough swipes to cross any screen in this app; a bound stops a missing element spinning. */
    private static final int MAX_SCROLL_SWIPES = 8;

    protected final AppiumDriver driver = DriverManager.driver();
    protected final FrameworkConfig config = FrameworkConfig.get();

    /** Locator that identifies this screen as loaded. */
    protected abstract By rootLocator();

    /** Blocks until this screen is on display. Call it from the screen's constructor path. */
    public void awaitLoaded() {
        try {
            wait(config.elementTimeout()).until(ExpectedConditions.visibilityOfElementLocated(rootLocator()));
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "%s did not appear within %ds. Expected root locator: %s"
                            .formatted(
                                    getClass().getSimpleName(),
                                    config.elementTimeout().toSeconds(),
                                    rootLocator()),
                    e);
        }
    }

    public boolean isDisplayed() {
        return isDisplayed(rootLocator(), Duration.ofSeconds(3));
    }

    // ------------------------------------------------------------- interaction

    protected WebElement visible(By locator) {
        return wait(config.elementTimeout()).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement clickable(By locator) {
        return wait(config.elementTimeout()).until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected List<WebElement> allVisible(By locator) {
        return wait(config.elementTimeout()).until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
    }

    protected void tap(By locator) {
        clickable(locator).click();
    }

    /**
     * Clears the field and types into it, then dismisses the keyboard.
     *
     * <p>The keyboard matters: on both platforms a raised keyboard can cover the very control the
     * next step needs to tap, and the resulting failure looks like a missing element rather than
     * an obscured one.
     */
    protected void type(By locator, String text) {
        WebElement field = visible(locator);
        field.clear();
        field.sendKeys(text);
        hideKeyboard();
    }

    protected void hideKeyboard() {
        try {
            if (driver instanceof io.appium.java_client.HidesKeyboard hides) {
                hides.hideKeyboard();
            }
        } catch (RuntimeException e) {
            // No keyboard was showing. Not a problem, and not worth a log line.
        }
    }

    protected boolean isDisplayed(By locator, Duration timeout) {
        try {
            return wait(timeout)
                    .until(ExpectedConditions.visibilityOfElementLocated(locator))
                    .isDisplayed();
        } catch (TimeoutException | NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Reads the text of an element, following through to its text-bearing descendants.
     *
     * <p>React Native renders a labelled control as a wrapper carrying the {@code testID} with the
     * actual text one level down. On Android that is an {@code android.view.ViewGroup} with the
     * {@code content-desc} and an {@code android.widget.TextView} child holding the string; on iOS
     * it is an {@code XCUIElementTypeOther} with the accessibility id and an
     * {@code XCUIElementTypeStaticText} child. Calling {@code getText()} on what the locator
     * matched therefore returns an empty string, not the label — a silent wrong answer rather
     * than an error, which is what makes it worth handling once, here.
     *
     * <p>Element-scoped class-name lookups, not XPath: still one hop, still ADR-003 compliant.
     */
    protected String textOf(By locator) {
        return textOf(visible(locator));
    }

    protected String textOf(WebElement element) {
        String own = element.getText();
        if (own != null && !own.isBlank()) {
            return own.trim();
        }
        return element.findElements(textNodeLocator()).stream()
                .map(WebElement::getText)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    private By textNodeLocator() {
        return isAndroid()
                ? AppiumBy.className("android.widget.TextView")
                : AppiumBy.className("XCUIElementTypeStaticText");
    }

    // ------------------------------------------------------------------ helpers

    // ------------------------------------------------------------- scrolling

    /**
     * Scrolls until {@code locator} is on screen, then returns it.
     *
     * <p>Deliberately a W3C pointer gesture rather than {@code UiScrollable}. The usual Android
     * answer is {@code new UiSelector().scrollIntoView(...)}, but that is the
     * {@code -android uiautomator} strategy, which ADR-003 bans and {@code checkNoXPath} enforces
     * — and it has no iOS equivalent, so a suite that leans on it grows a platform branch at every
     * long form. A synthesised swipe works identically on both platforms and needs no locator
     * strategy at all.
     */
    protected WebElement scrollUntilVisible(By locator) {
        for (int attempt = 0; attempt < MAX_SCROLL_SWIPES; attempt++) {
            if (isDisplayed(locator, Duration.ofMillis(500))) {
                return driver.findElement(locator);
            }
            swipeUp();
        }
        throw new TimeoutException("%s was not reachable after %d swipes".formatted(locator, MAX_SCROLL_SWIPES));
    }

    /** One short upward swipe through the middle of the screen. */
    protected void swipeUp() {
        Dimension size = driver.manage().window().getSize();
        int x = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.70);
        int endY = (int) (size.getHeight() * 0.30);

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, startY))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerMove(Duration.ofMillis(400), PointerInput.Origin.viewport(), x, endY))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        driver.perform(List.of(swipe));
    }

    /** Scrolls the control into view before tapping it — long forms need this. */
    protected void scrollAndTap(By locator) {
        scrollUntilVisible(locator);
        tap(locator);
    }

    /** Scrolls the field into view before typing into it. */
    protected void scrollAndType(By locator, String text) {
        scrollUntilVisible(locator);
        type(locator, text);
    }

    // ------------------------------------------------------------------ helpers

    protected WebDriverWait wait(Duration timeout) {
        return new WebDriverWait(driver, timeout);
    }

    protected MobilePlatform platform() {
        return DriverManager.platform();
    }

    protected boolean isAndroid() {
        return platform().isAndroid();
    }
}
