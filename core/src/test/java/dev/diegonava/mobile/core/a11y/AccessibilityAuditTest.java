package dev.diegonava.mobile.core.a11y;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegonava.mobile.core.a11y.AccessibilityFinding.Rule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The audit runs against XML, so the cases worth testing are trivial to write here and a nuisance
 * to stage on a device: an undersized button, a control with no label, two controls announcing the
 * same thing.
 */
class AccessibilityAuditTest {

    /** 420dpi, matching the emulator this framework is developed against: 1dp = 2.625px. */
    private static final AccessibilityAudit AUDIT = AccessibilityAudit.forDensity(420);

    @Test
    @DisplayName("a well-built screen produces no findings")
    void cleanScreenPasses() {
        String xml = """
                <hierarchy>
                  <android.widget.Button content-desc="Login button" clickable="true" bounds="[0,0][300,150]"/>
                  <android.widget.TextView text="Products" clickable="false" bounds="[0,200][300,240]"/>
                </hierarchy>""";

        assertThat(AUDIT.audit(xml)).isEmpty();
    }

    @Test
    @DisplayName("an interactive element with no label is reported")
    void missingLabelIsReported() {
        String xml = """
                <hierarchy>
                  <android.widget.ImageView clickable="true" bounds="[0,0][300,300]"/>
                </hierarchy>""";

        List<AccessibilityFinding> findings = AUDIT.audit(xml);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).rule()).isEqualTo(Rule.MISSING_LABEL);
        assertThat(findings.get(0).detail()).contains("announces nothing");
        assertThat(findings.get(0).signature()).doesNotContain("[");
    }

    @Test
    @DisplayName("a control below 48dp is reported with its measured size")
    void smallTouchTargetIsReported() {
        // 100px at 420dpi is ~38dp, comfortably under the 48dp minimum.
        String xml = """
                <hierarchy>
                  <android.widget.Button content-desc="Close" clickable="true" bounds="[0,0][100,100]"/>
                </hierarchy>""";

        List<AccessibilityFinding> findings = AUDIT.audit(xml);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).rule()).isEqualTo(Rule.TOUCH_TARGET_TOO_SMALL);
        assertThat(findings.get(0).detail()).contains("38x38dp").contains("48dp");
    }

    @Test
    @DisplayName("target size is judged in dp, so the same pixel size passes on a denser screen")
    void densityIsTakenIntoAccount() {
        String xml = """
                <hierarchy>
                  <android.widget.Button content-desc="Close" clickable="true" bounds="[0,0][130,130]"/>
                </hierarchy>""";

        // 130px is ~50dp at 420dpi — fine. The same 130px at 640dpi is ~33dp — not fine.
        assertThat(AccessibilityAudit.forDensity(420).audit(xml)).isEmpty();
        assertThat(AccessibilityAudit.forDensity(640).audit(xml))
                .extracting(AccessibilityFinding::rule)
                .containsExactly(Rule.TOUCH_TARGET_TOO_SMALL);
    }

    @Test
    @DisplayName("two controls sharing a label are reported once, naming both")
    void duplicateLabelsAreReportedOnce() {
        String xml = """
                <hierarchy>
                  <android.widget.Button content-desc="Add" clickable="true" bounds="[0,0][300,300]"/>
                  <android.widget.Button content-desc="Add" clickable="true" bounds="[0,400][300,700]"/>
                  <android.widget.Button content-desc="Add" clickable="true" bounds="[0,800][300,1100]"/>
                </hierarchy>""";

        List<AccessibilityFinding> findings = AUDIT.audit(xml).stream()
                .filter(f -> f.rule() == Rule.DUPLICATE_LABEL)
                .toList();

        assertThat(findings)
                .as("one finding per duplicated label, not per duplicate")
                .hasSize(1);
        assertThat(findings.get(0).detail()).contains("Add");
    }

    @Test
    @DisplayName("non-interactive layout containers are ignored entirely")
    void layoutContainersAreNotAudited() {
        String xml = """
                <hierarchy>
                  <android.view.ViewGroup clickable="false" bounds="[0,0][10,10]"/>
                  <android.widget.TextView text="" clickable="false" bounds="[0,0][5,5]"/>
                </hierarchy>""";

        assertThat(AUDIT.audit(xml))
                .as("auditing every container would bury the real findings")
                .isEmpty();
    }

    @Test
    @DisplayName("iOS element types are recognised as interactive without a clickable attribute")
    void iosElementsAreAudited() {
        String xml = """
                <hierarchy>
                  <XCUIElementTypeButton name="" bounds="[0,0][300,300]"/>
                </hierarchy>""";

        assertThat(AUDIT.audit(xml)).extracting(AccessibilityFinding::rule).containsExactly(Rule.MISSING_LABEL);
    }

    @Test
    @DisplayName("checkable and long-clickable elements count as interactive")
    void otherInteractiveFlagsCount() {
        String xml = """
                <hierarchy>
                  <android.widget.CheckBox checkable="true" bounds="[0,0][300,300]"/>
                  <android.widget.TextView long-clickable="true" bounds="[0,400][300,700]"/>
                </hierarchy>""";

        assertThat(AUDIT.audit(xml)).hasSize(2).allSatisfy(f -> assertThat(f.rule())
                .isEqualTo(Rule.MISSING_LABEL));
    }

    @Test
    @DisplayName("a finding's signature survives the element moving on screen")
    void signatureIsIndependentOfPosition() {
        String top = """
                <hierarchy>
                  <android.widget.Button content-desc="Close" clickable="true" bounds="[0,0][100,100]"/>
                </hierarchy>""";
        String scrolledDown = """
                <hierarchy>
                  <android.widget.Button content-desc="Close" clickable="true" bounds="[0,900][100,1000]"/>
                </hierarchy>""";

        // The baseline matches on signature, so a list that has scrolled a few pixels must not
        // read as a screen full of brand new problems.
        assertThat(AUDIT.audit(top).get(0).signature())
                .isEqualTo(AUDIT.audit(scrolledDown).get(0).signature())
                .doesNotContain("[");
    }

    @Test
    @DisplayName("unlabelled findings collapse to one signature per element type")
    void unlabelledFindingsCollapseByType() {
        String xml = """
                <hierarchy>
                  <android.view.ViewGroup clickable="true" bounds="[0,0][300,300]"/>
                  <android.view.ViewGroup clickable="true" bounds="[0,400][300,700]"/>
                </hierarchy>""";

        // "product cards announce nothing" is one problem to accept or fix, not six.
        assertThat(AUDIT.audit(xml))
                .extracting(AccessibilityFinding::signature)
                .containsOnly("MISSING_LABEL :: android.view.ViewGroup :: <no label>");
    }

    @Test
    @DisplayName("a zero or negative density is rejected rather than dividing by it")
    void densityMustBePositive() {
        assertThatThrownBy(() -> AccessibilityAudit.forDensity(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("empty page source is rejected outright")
    void emptySourceIsRejected() {
        assertThatThrownBy(() -> AUDIT.audit("")).isInstanceOf(IllegalArgumentException.class);
    }
}
