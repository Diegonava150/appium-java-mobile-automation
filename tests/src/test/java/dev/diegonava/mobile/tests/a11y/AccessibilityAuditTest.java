package dev.diegonava.mobile.tests.a11y;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.a11y.AccessibilityAudit;
import dev.diegonava.mobile.core.a11y.AccessibilityBaseline;
import dev.diegonava.mobile.core.a11y.AccessibilityFinding;
import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.AdbClient;
import dev.diegonava.mobile.core.driver.DriverManager;
import dev.diegonava.mobile.core.junit.EnabledOnPlatform;
import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.screens.App;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Audits the app's main screens for accessibility problems.
 *
 * <p>Gates on <i>new</i> findings rather than all of them. The app under test is a third-party
 * release with pre-existing debt that cannot be fixed from here, and an audit that fails on all of
 * it would be permanently red — which is how a check ends up switched off. The existing debt is
 * recorded in {@code a11y-baseline.txt} and anything beyond it fails, the same shape as the flake
 * ledger in ADR-006.
 *
 * <p>Android only for now: density comes from {@code wm density}, and the iOS equivalent needs a
 * different route. The audit itself is platform-neutral and already recognises iOS element types.
 */
@MobileTest
@EnabledOnPlatform(value = MobilePlatform.ANDROID, reason = "display density is read through adb")
@Epic("Accessibility")
@Feature("Static audit")
@DisplayName("Accessibility audit")
class AccessibilityAuditTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("the catalog introduces no new accessibility findings")
    void catalogHasNoNewFindings() {
        App.launch();
        auditCurrentScreen("catalog");
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("the login screen introduces no new accessibility findings")
    void loginScreenHasNoNewFindings() {
        App.launch();
        App.navigation().openLogin();
        auditCurrentScreen("login");
    }

    private void auditCurrentScreen(String screen) {
        AdbClient adb = AdbClient.forSlot(DriverManager.slot());
        AccessibilityAudit audit = AccessibilityAudit.forDensity(adb.displayDensityDpi());

        List<AccessibilityFinding> findings = audit.audit(DriverManager.driver().getPageSource());

        Allure.addAttachment(
                "accessibility findings (" + screen + ")",
                "text/plain",
                findings.isEmpty()
                        ? "No findings."
                        : findings.stream()
                                .map(AccessibilityFinding::toString)
                                .collect(Collectors.joining(System.lineSeparator())));

        AccessibilityBaseline baseline = AccessibilityBaseline.load();

        if (!baseline.exists()) {
            // First run against this app. Report everything and pass, so the accepted set can be
            // recorded deliberately rather than a red build being the way anyone finds out.
            Allure.addAttachment(
                    "a11y-baseline.txt to accept (" + screen + ")",
                    "text/plain",
                    AccessibilityBaseline.asBaselineFile(screen, findings));
            System.out.println(AccessibilityBaseline.asBaselineFile(screen, findings));
            return;
        }

        List<AccessibilityFinding> regressions = baseline.regressions(findings);

        assertThat(regressions)
                .as(
                        "%d accessibility finding(s) on the %s screen are not in the accepted baseline of %d."
                                + " Fix them, or add their signatures to a11y-baseline.txt with a reason:%n%s",
                        regressions.size(),
                        screen,
                        baseline.size(),
                        regressions.stream()
                                .map(AccessibilityBaseline::signatureOf)
                                .collect(Collectors.joining(System.lineSeparator())))
                .isEmpty();
    }
}
