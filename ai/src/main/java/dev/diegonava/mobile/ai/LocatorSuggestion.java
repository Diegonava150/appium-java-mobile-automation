package dev.diegonava.mobile.ai;

import io.appium.java_client.AppiumBy;
import java.util.Locale;
import java.util.Optional;
import org.openqa.selenium.By;

/**
 * What the model proposed, and whether this framework is willing to use it.
 *
 * <p>{@link #toBy()} is the interesting method, and it is a policy gate rather than a translator.
 * A model looking at a page source will happily suggest {@code //android.widget.Button[3]} — it is
 * the locator most likely to match, and it is the worst possible thing to put in a test suite.
 * ADR-003 bans XPath and {@code ./gradlew checkNoXPath} enforces that against the source tree; a
 * fallback that could synthesise one at runtime would be a hole straight through that rule.
 *
 * <p>So exactly two strategies are permitted, both stable identifiers. Anything else — XPath, iOS
 * class chains, UiAutomator selectors, text matching, coordinates — is refused, and the refusal is
 * logged. Refusing means the original failure stands, which is the correct outcome: a test that
 * fails because a {@code testID} was renamed is a useful signal, and swapping it for an index-based
 * locator would convert that signal into a test that passes until it silently tests the wrong
 * button.
 */
public record LocatorSuggestion(String strategy, String value, String reasoning) {

    /**
     * The suggestion as a locator, if policy permits it.
     *
     * @return empty when the strategy is not one this framework allows, or the value is unusable
     */
    public Optional<By> toBy() {
        if (strategy == null || value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = strategy.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return switch (normalised) {
            case "accessibility_id", "accessibilityid", "accessibility" ->
                Optional.of(AppiumBy.accessibilityId(value.strip()));
            case "id", "resource_id", "resourceid" -> Optional.of(AppiumBy.id(value.strip()));
            default -> Optional.empty();
        };
    }

    /** Why the suggestion was refused, for the log line. Only meaningful when {@link #toBy()} is empty. */
    public String rejection() {
        if (value == null || value.isBlank()) {
            return "the model returned an empty locator value";
        }
        return "strategy '%s' is not permitted — this framework allows only accessibility id and id (ADR-003)"
                .formatted(strategy);
    }
}
