package dev.diegonava.mobile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The policy gate, which is the part of the AI layer most worth pinning down.
 *
 * <p>Everything here runs with no key and no network, because none of it is about the model. It is
 * about what this framework will accept from a model, and that is a property of the code.
 */
class LocatorSuggestionTest {

    @Test
    @DisplayName("an accessibility id is accepted — the strategy the whole suite is built on")
    void accessibilityIdIsAccepted() {
        var suggestion = new LocatorSuggestion("accessibility id", "cart tab", "matches the icon top right");

        assertThat(suggestion.toBy()).isPresent();
        assertThat(suggestion.toBy().orElseThrow().toString()).contains("cart tab");
    }

    @Test
    @DisplayName("a resource id is accepted as the second-choice stable identifier")
    void resourceIdIsAccepted() {
        assertThat(new LocatorSuggestion("id", "com.example:id/cart", "").toBy())
                .isPresent();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "xpath",
                "-android uiautomator", // xpath-ok: the subject of the test is that this is refused
                "-ios class chain",
                "-ios predicate string",
                "css selector",
                "class name",
                "text",
                "coordinates",
            })
    @DisplayName("every banned strategy is refused, however plausible the value")
    void bannedStrategiesAreRefused(String strategy) {
        // The point: the model can suggest these, and it will — an XPath is the locator most
        // likely to match. Accepting one would let the fallback punch a hole through ADR-003 at
        // runtime that checkNoXPath could never see, because no such locator exists in the source.
        var suggestion = new LocatorSuggestion(strategy, "//android.widget.Button[3]", "third button");

        assertThat(suggestion.toBy()).isEmpty();
        assertThat(suggestion.rejection()).contains(strategy).contains("ADR-003");
    }

    @Test
    @DisplayName("an empty value is refused even under a permitted strategy")
    void emptyValueIsRefused() {
        // The prompt tells the model to answer this way when the element genuinely is not there.
        // It is a correct answer, and it must not turn into By.accessibilityId("").
        var suggestion = new LocatorSuggestion("accessibility id", "", "the element is not on this screen");

        assertThat(suggestion.toBy()).isEmpty();
        assertThat(suggestion.rejection()).contains("empty");
    }

    @Test
    @DisplayName("a null strategy is refused rather than throwing")
    void nullsAreRefused() {
        assertThat(new LocatorSuggestion(null, "cart tab", null).toBy()).isEmpty();
        assertThat(new LocatorSuggestion("accessibility id", null, null).toBy()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accessibility id", "ACCESSIBILITY_ID", "Accessibility-Id", "  accessibility  id  "})
    @DisplayName("strategy names are matched loosely, because the model will not be consistent")
    void strategyMatchingIsForgiving(String strategy) {
        assertThat(new LocatorSuggestion(strategy, "cart tab", "").toBy()).isPresent();
    }

    @Test
    @DisplayName("surrounding whitespace is stripped from the value")
    void valueIsStripped() {
        assertThat(new LocatorSuggestion("accessibility id", "  cart tab \n", "")
                        .toBy()
                        .orElseThrow()
                        .toString())
                .contains("cart tab")
                .doesNotContain("\n");
    }
}
