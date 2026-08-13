package dev.diegonava.mobile.ai.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TriagePromptTest {

    @Test
    @DisplayName("the prompt asks for a hypothesis with evidence, not a verdict")
    void asksForAHypothesis() {
        assertThat(TriagePrompt.SYSTEM).contains("hypothesis, not a verdict").contains("tie every claim");
    }

    @Test
    @DisplayName("the prompt forbids recommending a retry")
    void forbidsRetryAsAFix() {
        // The whole position of ADR-006 is that "flaky, re-run it" is a diagnosis nobody has made
        // yet. A triage note that says it is worse than no note, because it sounds authoritative
        // and someone will act on it.
        assertThat(TriagePrompt.SYSTEM).contains("Never recommend a retry");
    }

    @Test
    @DisplayName("the prompt names obstruction as a category to look for specifically")
    void looksForObstructions() {
        // This is the one that earned its place. The iOS "Save Password?" dialog sitting over the
        // catalog was invisible in every log and obvious in the screenshot, and it presented as
        // "the screen is present, fully populated, and untappable".
        assertThat(TriagePrompt.SYSTEM).contains("system dialog").contains("invisible in every log");
    }

    @Test
    @DisplayName("the prompt permits an inconclusive answer")
    void permitsInconclusive() {
        assertThat(TriagePrompt.SYSTEM)
                .contains("does not support a conclusion")
                .contains("name what is missing");
    }

    @Test
    @DisplayName("missing evidence is described rather than sent as a blank section")
    void missingEvidenceIsExplained() {
        String prompt = TriagePrompt.user("LoginTest.signsIn.ios", "", "", "");

        assertThat(prompt)
                .contains("LoginTest.signsIn.ios")
                .contains("no message was recorded")
                .contains("did not return a hierarchy")
                .contains("iOS runs do not collect logcat")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("the note renders every field of the diagnosis and labels itself a hypothesis")
    void renderedNoteIsComplete() {
        FailureBundle bundle = new FailureBundle(
                "CheckoutTest.buys.android", Optional.of(Path.of("x.png")), Optional.empty(), Optional.empty());
        FailureTriage.Diagnosis diagnosis = new FailureTriage.Diagnosis(
                "obstruction",
                "high",
                "A keyboard is covering the submit button.",
                "The screenshot shows the numeric keypad over the lower third.",
                "Dismiss the keyboard before scrolling to the button.");

        String note = FailureTriage.render(bundle, diagnosis);

        assertThat(note)
                .contains("CheckoutTest.buys.android")
                .contains("obstruction")
                .contains("high")
                .contains("numeric keypad")
                .contains("Dismiss the keyboard")
                .contains("hypothesis, not a finding");
    }
}
