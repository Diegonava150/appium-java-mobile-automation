package dev.diegonava.mobile.ai;

import org.openqa.selenium.By;

/**
 * What the model is asked.
 *
 * <p>Separated from the call so it can be read and asserted on. A prompt buried in a string
 * concatenation three levels inside an HTTP client is a prompt nobody reviews, and the prompt is
 * the part of an AI integration most likely to be quietly wrong.
 *
 * <p>The system prompt states the locator policy up front rather than filtering the answer
 * afterwards. Both happen — {@link LocatorSuggestion#toBy()} still refuses anything outside the
 * policy — but telling the model the rule gets a usable answer instead of a rejected one.
 */
public final class LocatorPrompt {

    static final String SYSTEM = """
            You are helping a mobile test suite recover from a locator that matched no element.

            You will be given a screenshot of the current screen, a filtered view of the accessibility
            hierarchy, the locator that failed, and a description of the element it was meant to find.

            Identify that element and return the locator that will find it. Two strategies are
            permitted and nothing else:

              - "accessibility id": the element's content-desc (Android) or accessibility identifier
                (iOS). Strongly preferred — this app is React Native, so both come from one testID.
              - "id": the element's resource-id (Android) or name (iOS).

            Never return XPath, an iOS class chain, a UiAutomator selector, a text match, or
            coordinates. This suite bans positional and text-based locators, and a suggestion using
            one will be discarded, leaving the test failed. If the element is genuinely not on the
            screen, or it has no stable identifier, say so in the reasoning and return
            an empty value — that is a correct and useful answer.

            Prefer the identifier that most resembles the one that failed: the usual cause is a
            testID that was renamed, not an element that moved.
            """;

    private LocatorPrompt() {}

    public static String user(By failedLocator, String description, String hierarchyDigest) {
        return """
                The locator that failed:
                  %s

                What it was meant to find:
                  %s

                Nodes in the current hierarchy that carry an identifier or visible text:
                %s
                """.formatted(
                failedLocator,
                description == null || description.isBlank() ? "(not described)" : description,
                hierarchyDigest.isBlank() ? "  (none — the hierarchy carried no identifiers at all)" : hierarchyDigest);
    }
}
