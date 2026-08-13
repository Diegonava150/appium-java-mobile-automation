package dev.diegonava.mobile.ai.triage;

/**
 * What the triage step asks.
 *
 * <p>Written to produce a hypothesis with its evidence attached, not a verdict. A triage note
 * that says "flaky test, re-run it" is worse than no note: it is a confident-sounding excuse
 * that will be believed, and this framework's entire position on flakiness (ADR-006) is that
 * "flaky" is a diagnosis nobody has made yet.
 */
public final class TriagePrompt {

    static final String SYSTEM = """
            You are triaging a failed mobile UI test. You have the evidence captured at the moment
            the test threw: a screenshot of the device, the native view hierarchy, and — on Android
            — the tail of the device log.

            Produce a hypothesis, not a verdict, and tie every claim to something visible in the
            evidence. Distinguish clearly between these, because they need completely different
            responses:

              - a real defect in the application under test
              - a broken or outdated locator in the test suite
              - a timing problem: the test acted before the app was ready
              - an environment problem: the emulator, the driver, the network
              - an obstruction: a system dialog, a permission prompt, or a keyboard covering the
                control the test was trying to reach

            That last one is worth looking for specifically. It is invisible in every log and
            obvious in the screenshot, and it is the failure mode most often misdiagnosed as
            flakiness.

            If the evidence does not support a conclusion, say so and name what is missing. An
            honest "the screenshot shows the expected screen and nothing in the log explains this"
            is a useful triage note. A guess dressed as a finding is not — someone will act on it.

            Never recommend a retry as the fix. A test that passes on the second attempt for an
            unexplained reason is an unexplained failure, not a solved one.
            """;

    private TriagePrompt() {}

    public static String user(String testId, String failureMessage, String pageSource, String logcatTail) {
        return """
                Failed test:
                  %s

                What the test reported:
                  %s

                View hierarchy at the moment of failure:
                %s

                Device log (tail):
                %s
                """.formatted(
                        testId,
                        blankTo(failureMessage, "(no message was recorded alongside the artifacts)"),
                        blankTo(pageSource, "  (not captured — the device did not return a hierarchy)"),
                        blankTo(logcatTail, "  (not captured — iOS runs do not collect logcat)"));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
