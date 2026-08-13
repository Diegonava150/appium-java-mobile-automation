package dev.diegonava.mobile.core.ai;

/**
 * One occasion where a locator failed and a fallback found the element anyway.
 *
 * @param testId the test that was running
 * @param screen the screen object whose locator failed
 * @param failedLocator the locator that matched nothing
 * @param resolvedBy how the element was eventually found
 * @param evidence where the supporting screenshot was written
 */
public record LocatorHealingEvent(
        String testId, String screen, String failedLocator, String resolvedBy, String evidence) {

    /** Stable identity for the ledger: which locator, on which screen. Not which run. */
    public String signature() {
        return screen + " :: " + failedLocator;
    }
}
