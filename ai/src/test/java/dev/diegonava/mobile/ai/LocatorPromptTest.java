package dev.diegonava.mobile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.appium.java_client.AppiumBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The prompt is the part of an AI integration most likely to be quietly wrong, and the part least
 * likely to be reviewed. These are cheap tests, and they are the ones that catch someone editing
 * the policy out of the system prompt while leaving the ADR in place.
 */
class LocatorPromptTest {

    @Test
    @DisplayName("the system prompt states the locator policy rather than relying on the filter")
    void systemPromptCarriesThePolicy() {
        // Both layers exist. Telling the model the rule gets a usable answer; the filter in
        // LocatorSuggestion is what makes it enforceable when the model ignores it anyway.
        assertThat(LocatorPrompt.SYSTEM)
                .contains("accessibility id")
                .contains("XPath")
                .contains("testID");
    }

    @Test
    @DisplayName("the system prompt allows the model to say the element is not there")
    void refusalIsPermitted() {
        // Without this, a model asked to produce a locator will produce one, and the fallback
        // becomes a machine for inventing plausible selectors for elements that do not exist.
        assertThat(LocatorPrompt.SYSTEM).contains("empty value").contains("correct and useful answer");
    }

    @Test
    @DisplayName("the user turn names the failed locator, the element, and the hierarchy")
    void userPromptCarriesTheEvidence() {
        String prompt = LocatorPrompt.user(
                AppiumBy.accessibilityId("cart tab"), "the cart tab", "<node content-desc=\"cart\" />");

        assertThat(prompt).contains("cart tab").contains("the cart tab").contains("content-desc");
    }

    @Test
    @DisplayName("a missing description degrades to a marker instead of the word null")
    void handlesMissingDescription() {
        assertThat(LocatorPrompt.user(AppiumBy.accessibilityId("x"), null, "<node name=\"x\" />"))
                .contains("(not described)")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("an empty hierarchy is stated as such, since it is diagnostic on its own")
    void emptyHierarchyIsExplained() {
        // A hierarchy with no identifiers at all usually means the screen has not rendered, or a
        // system dialog is sitting over it. Saying so beats sending a blank section.
        assertThat(LocatorPrompt.user(AppiumBy.accessibilityId("x"), "a button", ""))
                .contains("no identifiers at all");
    }
}
