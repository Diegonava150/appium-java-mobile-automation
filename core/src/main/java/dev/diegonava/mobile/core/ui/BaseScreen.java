package dev.diegonava.mobile.core.ui;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.driver.DriverManager;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(BaseScreen.class);

    /**
     * How many times {@link #type} will retype a field whose contents come back wrong.
     *
     * <p>Three, because the observed failure drops a character or a whole field on one attempt and
     * not the next. A field that is wrong three times running is not a race.
     */
    private static final int TYPE_ATTEMPTS = 3;

    /** Enough swipes to cross any screen in this app; a bound stops a missing element spinning. */
    private static final int MAX_SCROLL_SWIPES = 8;

    protected final AppiumDriver driver = DriverManager.driver();
    protected final FrameworkConfig config = FrameworkConfig.get();

    /** Locator that identifies this screen as loaded. */
    protected abstract By rootLocator();

    /**
     * Blocks until this screen is on display. Call it from the screen's constructor path.
     *
     * <p>Android waits for visibility; iOS waits only for presence. That asymmetry is not
     * laziness, it is a genuine difference in what the two automation stacks mean by "visible".
     * XCUITest computes visibility from an element's own frame, and React Native's screen-level
     * wrapper is a zero-size {@code XCUIElementTypeOther} whose children carry the actual layout —
     * so the container reports invisible while everything inside it is plainly on screen. The
     * first real simulator run failed exactly this way: "element #0 was invisible" for a locator
     * that had matched.
     *
     * <p>Scoped to the screen-level container on purpose. Interactive elements still wait for
     * visibility on both platforms, because for a control that a user has to tap, XCUITest's
     * stricter definition is the one that is actually correct.
     */
    public void awaitLoaded() {
        try {
            wait(config.elementTimeout()).until(onScreen(rootLocator()));
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
        try {
            return wait(config.elementTimeout()).until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            return recover(locator, e);
        }
    }

    protected WebElement clickable(By locator) {
        try {
            return wait(config.elementTimeout()).until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException e) {
            return recover(locator, e);
        }
    }

    /**
     * The one place a locator fallback is ever consulted.
     *
     * <p>Three properties are worth being explicit about, because they are what separate this from
     * the self-healing feature it superficially resembles:
     *
     * <ol>
     *   <li>It runs only after the full explicit wait has already expired. Nothing on the happy
     *       path touches it, so with no fallback registered — the default, and the case when the
     *       {@code :ai} module is simply absent — this method is a rethrow and timing is unchanged.
     *   <li>The original {@link TimeoutException} is what propagates when recovery fails. It names
     *       the locator and the screen; an exception from the fallback would name neither.
     *   <li>Success is not free. The implementation in {@code :ai} records the heal as debt with
     *       the screenshot that justified it, and unrecognised debt fails the build. The run is
     *       rescued; the change in the app is not hidden. See ADR-009.
     * </ol>
     */
    private WebElement recover(By locator, TimeoutException original) {
        return LocatorFallback.discover()
                .flatMap(fallback -> fallback.locate(driver, locator, getClass().getSimpleName()))
                .orElseThrow(() -> original);
    }

    protected List<WebElement> allVisible(By locator) {
        return wait(config.elementTimeout()).until(allOnScreen(locator));
    }

    /**
     * "On screen" as each platform actually defines it.
     *
     * <p>XCUITest computes visibility from an element's own frame. React Native renders its
     * containers as zero-size {@code XCUIElementTypeOther} wrappers whose children carry the
     * layout, so a container reports invisible while everything inside it is plainly on screen —
     * the first simulator run failed with "element #0 was invisible" for locators that had
     * matched perfectly well. UiAutomator2 does not have this problem, so Android keeps the
     * stricter check.
     *
     * <p>Applies to container and screen-level lookups only. {@link #visible} and
     * {@link #clickable}, which back every tap and every keystroke, still demand real visibility
     * on both platforms — for a control a user has to reach, XCUITest's stricter definition is
     * the correct one.
     */
    private ExpectedCondition<WebElement> onScreen(By locator) {
        return isAndroid()
                ? ExpectedConditions.visibilityOfElementLocated(locator)
                : ExpectedConditions.presenceOfElementLocated(locator);
    }

    private ExpectedCondition<List<WebElement>> allOnScreen(By locator) {
        return isAndroid()
                ? ExpectedConditions.visibilityOfAllElementsLocatedBy(locator)
                : ExpectedConditions.presenceOfAllElementsLocatedBy(locator);
    }

    protected void tap(By locator) {
        clickable(locator).click();
    }

    /**
     * Waits for an already-located element to become genuinely interactive, then returns it.
     *
     * <p>Needed wherever an element arrives through a container lookup rather than its own
     * locator, because on iOS those lookups accept presence — see {@link #allOnScreen}. Presence
     * means "in the accessibility tree", which is true well before a list row has been laid out.
     * Tapping at that moment does nothing at all: no error, no navigation, and a failure that
     * surfaces one screen later as "the next screen did not appear".
     */
    protected WebElement awaitClickable(WebElement element) {
        return wait(config.elementTimeout()).until(ExpectedConditions.elementToBeClickable(element));
    }

    /**
     * Clears the field, types into it, checks that the text actually arrived, and dismisses the
     * keyboard.
     *
     * <p>The keyboard matters: on both platforms a raised keyboard can cover the very control the
     * next step needs to tap, and the resulting failure looks like a missing element rather than
     * an obscured one.
     *
     * <p>The check matters more, and it was added after a green-looking suite spent weeks lying.
     * {@code sendKeys} into a React Native field on iOS silently drops characters. A sign-in typed
     * {@code bb@example.com} for {@code bob@example.com} and an empty password, the app said
     * "Provided credentials do not match any user in this service" — and the test failed twenty
     * seconds later with <em>"CatalogScreen did not appear"</em>, which is true, useless, and three
     * steps removed from the cause. It had been dismissed as an infrastructure flake more than once.
     *
     * <p>So the text is read back. A retry usually settles it, and if it does not, the failure names
     * the field and quotes what is actually in it.
     */
    protected void type(By locator, String text) {
        String actual = null;
        for (int attempt = 1; attempt <= TYPE_ATTEMPTS; attempt++) {
            WebElement field = visible(locator);
            field.clear();
            field.sendKeys(text);

            actual = readBack(field);
            if (arrivedIntact(text, actual)) {
                hideKeyboard();
                return;
            }
            log.warn(
                    "Attempt {} of {}: typing into {} left \"{}\" in the field. Retrying.",
                    attempt,
                    TYPE_ATTEMPTS,
                    locator,
                    actual);
        }

        hideKeyboard();
        throw new IllegalStateException(
                ("Typed %d characters into %s %d times and the field still reads \"%s\". This is the "
                                + "dropped-keystroke failure, not a missing element: whatever is submitted next "
                                + "will be wrong, and the error it produces will point somewhere else.")
                        .formatted(text.length(), locator, TYPE_ATTEMPTS, actual));
    }

    /** What the field says it contains, or {@code null} when it will not say. */
    private String readBack(WebElement field) {
        try {
            String own = field.getText();
            return own == null ? null : own.trim();
        } catch (WebDriverException e) {
            // Unreadable is not the same as wrong. Handled by the caller as "cannot verify".
            return null;
        }
    }

    /**
     * Whether what came back is consistent with what was typed.
     *
     * <p>Three cases, and the middle one is why this is not an equality check. A plain field echoes
     * the text. A secure field echoes a row of bullets, so only the length can be compared — an
     * empty password field after typing eight characters is exactly the bug above, and has to stay
     * a failure. And a field that reports nothing at all cannot be judged either way, so it is
     * accepted rather than invented into a failure; that is the behaviour this method replaced, and
     * it is the safe direction to be wrong in.
     */
    // Package-private for TypedTextVerificationTest: this predicate decides whether a passing
    // test becomes a failing one, so it is the part that must not be reasoned about only in prose.
    static boolean arrivedIntact(String expected, String actual) {
        if (actual == null) {
            return true;
        }
        if (!actual.isEmpty() && actual.chars().allMatch(BaseScreen::isMaskCharacter)) {
            return actual.length() == expected.length();
        }
        return unformatted(expected).equalsIgnoreCase(unformatted(actual));
    }

    /**
     * Strips the punctuation a field inserts on the user's behalf.
     *
     * <p>Only separators, and only the ones fields actually add: a card number types as sixteen
     * digits and displays as {@code 4111 1111 1111 1111}, an expiry as {@code 12/25}, a phone
     * number in brackets. Comparing those literally is how this check failed four Android tests on
     * its first run, calling correct formatting a dropped keystroke.
     *
     * <p>Deliberately not {@code \W}, which would also strip {@code @} and {@code .} and make
     * {@code bobexample.com} indistinguishable from {@code bob@example.com}. The signal being
     * protected is characters going missing, so the only characters safe to ignore are ones the
     * field is known to supply itself.
     */
    private static String unformatted(String text) {
        return text.replaceAll("[\\s\\-/()]", "");
    }

    private static boolean isMaskCharacter(int character) {
        return character == '•' || character == '*' || character == '●' || character == '·';
    }

    /**
     * Dismisses the keyboard, by whichever means the platform actually honours.
     *
     * <p>{@code HidesKeyboard.hideKeyboard()} works on Android and quietly does nothing on iOS,
     * where there is no system-level dismiss — the keyboard goes away when something dismisses it,
     * usually a return key. XCUITest exposes {@code mobile: hideKeyboard} for exactly this, and it
     * takes the list of key labels worth trying.
     *
     * <p>Getting this wrong is expensive to diagnose because the failure lands somewhere else
     * entirely: the checkout address form filled in perfectly, then failed with "To Payment button
     * was not reachable after 8 swipes" — the button was on screen the whole time, behind the
     * keyboard, where no amount of scrolling could reach it.
     */
    protected void hideKeyboard() {
        try {
            if (isAndroid()) {
                if (driver instanceof io.appium.java_client.HidesKeyboard hides) {
                    hides.hideKeyboard();
                }
                return;
            }

            // Works for the alphabetic keyboard, which has a return key to press.
            try {
                driver.executeScript(
                        "mobile: hideKeyboard", Map.of("keys", List.of("return", "Done", "Next", "Go", "Search")));
            } catch (RuntimeException ignored) {
                // Falls through to the tap below.
            }

            // The numeric keypad has no dismiss key at all — just digits and a backspace — so
            // pressing keys cannot work and the checkout payment form stalls with its submit
            // button behind the keypad. Tapping outside is the only thing that closes it.
            //
            // Unconditional, not guarded on isKeyboardShown(). That guard was tried first and the
            // keypad stayed up: XCUITest reports keyboard state unreliably, so the fallback
            // skipped itself precisely when it was needed. The tap lands on the inert header, so
            // running it when no keyboard is up costs nothing.
            tapAboveKeyboard();
        } catch (RuntimeException e) {
            // No keyboard was showing. Not a problem, and not worth a log line.
        }
    }

    /**
     * Taps the app header to move focus off the field.
     *
     * <p>The header is the one region reliably present, inert, and clear of the keyboard on every
     * screen in this app. Centred horizontally on purpose: the back arrow sits on the left, and
     * tapping that would navigate away rather than dismiss anything.
     */
    private void tapAboveKeyboard() {
        Dimension size = driver.manage().window().getSize();
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(
                        Duration.ZERO, PointerInput.Origin.viewport(), size.getWidth() / 2, (int)
                                (size.getHeight() * 0.07)))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(List.of(tap));
    }

    protected boolean isDisplayed(By locator, Duration timeout) {
        try {
            wait(timeout).until(onScreen(locator));
            return true;
        } catch (TimeoutException | NoSuchElementException e) {
            return false;
        }
    }

    /** Strict visibility on both platforms, for logic that must not accept mere presence. */
    private boolean isVisibleNow(By locator, Duration timeout) {
        try {
            wait(timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
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
            // Genuine visibility, not the platform-relaxed onScreen() check. This loop exists to
            // decide whether something is on screen yet, so accepting mere presence would make it
            // answer "yes" for an element scrolled far out of view and return without scrolling
            // at all. That regression cost iOS two passing login tests: the submit button was
            // found, never scrolled to, and then failed the clickable check underneath the
            // keyboard.
            if (isVisibleNow(locator, Duration.ofMillis(500))) {
                return driver.findElement(locator);
            }
            swipeUp();
        }
        throw new TimeoutException("%s was not reachable after %d swipes".formatted(locator, MAX_SCROLL_SWIPES));
    }

    /**
     * One short upward swipe through the upper half of the screen.
     *
     * <p>Confined to the top half deliberately. The obvious choice is to drag from 70% to 30%,
     * which uses more of the screen — but a raised keyboard occupies roughly the bottom half, so
     * that gesture starts *on the keyboard* and scrolls nothing at all. The symptom is a form that
     * refuses to scroll with no error: "Review Order button was not reachable after 8 swipes",
     * eight swipes having been delivered to a keypad.
     *
     * <p>Staying above the keyboard means the gesture always lands on scrollable content, whether
     * a keyboard is up or not.
     */
    protected void swipeUp() {
        Dimension size = driver.manage().window().getSize();
        int x = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.45);
        int endY = (int) (size.getHeight() * 0.15);

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, startY))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerMove(Duration.ofMillis(400), PointerInput.Origin.viewport(), x, endY))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        driver.perform(List.of(swipe));
    }

    /** The reverse of {@link #swipeUp}, within the same keyboard-safe band. */
    protected void swipeDown() {
        Dimension size = driver.manage().window().getSize();
        int x = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.15);
        int endY = (int) (size.getHeight() * 0.45);

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
