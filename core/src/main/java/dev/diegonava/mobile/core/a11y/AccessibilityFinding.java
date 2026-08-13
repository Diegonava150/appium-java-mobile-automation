package dev.diegonava.mobile.core.a11y;

/**
 * One accessibility problem found on a screen.
 *
 * <p>Identity and location are deliberately separate fields. {@link #signature()} is what the
 * baseline matches on and it contains no coordinates; {@link #location} is for a human reading the
 * report. The first version of this conflated them, and a baseline keyed on pixel bounds would
 * have reported a fresh set of findings every time the list scrolled a few pixels or the test ran
 * at a different density — a check that cries wolf on every run is worse than no check.
 *
 * @param rule which check failed
 * @param elementType the platform's class name for the element
 * @param label what a screen reader would announce, empty when that is the problem
 * @param location pixel bounds, for finding the thing on screen
 * @param detail what was wrong, with the measured value where there is one
 */
public record AccessibilityFinding(Rule rule, String elementType, String label, String location, String detail) {

    /**
     * The checks this framework makes.
     *
     * <p>Sourced from Google's Accessibility Test Framework rule catalogue rather than invented.
     * ATF itself expects an Espresso or uiautomator view hierarchy and cannot be pointed at an
     * Appium page source, so the rules are reimplemented against the XML — which also means they
     * work unchanged on iOS, where ATF does not exist at all.
     */
    public enum Rule {
        /**
         * A control smaller than the minimum comfortable touch size.
         *
         * <p>48dp is the Material and WCAG 2.1 target-size guidance. Below it, people with motor
         * impairments and people on a moving train miss the same buttons.
         */
        TOUCH_TARGET_TOO_SMALL,

        /**
         * An interactive element a screen reader would announce as nothing at all.
         *
         * <p>The most consequential failure in the set: the control is unusable with TalkBack or
         * VoiceOver, and equally unreachable by this framework's own locator strategy (ADR-003) —
         * a neat demonstration that testability and accessibility are the same requirement.
         */
        MISSING_LABEL,

        /**
         * Two elements on one screen announcing the same thing.
         *
         * <p>Ambiguous to a screen-reader user, and ambiguous to automation for the same reason.
         */
        DUPLICATE_LABEL
    }

    /**
     * Stable identity for baselining: rule, element type, and label. No coordinates.
     *
     * <p>Findings with no label collapse to one signature per element type, which is the right
     * granularity — "product cards announce nothing" is one problem to accept or fix, not six.
     */
    public String signature() {
        return "%s :: %s :: %s".formatted(rule, elementType, label.isBlank() ? "<no label>" : label);
    }

    @Override
    public String toString() {
        return "%s | %s%s | %s".formatted(rule, elementType, location, detail);
    }
}
