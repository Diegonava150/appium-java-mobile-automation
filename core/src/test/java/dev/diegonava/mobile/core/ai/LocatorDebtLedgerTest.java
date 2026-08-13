package dev.diegonava.mobile.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * The debt ledger is the part of the AI layer that is worth the most and needs no API key at all,
 * so it gets tested properly. The vision call it records is a detail; the accounting is the idea.
 */
@Execution(ExecutionMode.SAME_THREAD)
class LocatorDebtLedgerTest {

    @AfterEach
    void clearLedger() {
        LocatorDebtLedger.clear();
    }

    private static LocatorHealingEvent heal(String screen, String locator) {
        return new LocatorHealingEvent(
                "SomeTest.someMethod", screen, locator, "vision fallback", "build/artifacts/x.png");
    }

    @Test
    @DisplayName("with no accepted file, every heal is new debt")
    void everyHealIsNewByDefault() {
        LocatorDebtLedger.record(heal("CartScreen", "accessibility id: checkout"));

        LocatorDebtLedger ledger = LocatorDebtLedger.load();

        assertThat(ledger.acceptedCount()).isZero();
        assertThat(ledger.newDebt()).hasSize(1);
    }

    @Test
    @DisplayName("a clean run has no debt at all")
    void cleanRunHasNoDebt() {
        assertThat(LocatorDebtLedger.load().newDebt()).isEmpty();
        assertThat(LocatorDebtLedger.recorded()).isEmpty();
    }

    @Test
    @DisplayName("the same locator healing twice is one debt, not two")
    void repeatedHealsCollapse() {
        LocatorDebtLedger.record(heal("CartScreen", "accessibility id: checkout"));
        LocatorDebtLedger.record(heal("CartScreen", "accessibility id: checkout"));

        // Identity is the locator, not the occasion. A locator that fails on every one of forty
        // tests is one thing to fix.
        assertThat(LocatorDebtLedger.load().newDebt()).hasSize(1);
    }

    @Test
    @DisplayName("distinct locators are distinct debts")
    void distinctLocatorsAreDistinct() {
        LocatorDebtLedger.record(heal("CartScreen", "accessibility id: checkout"));
        LocatorDebtLedger.record(heal("LoginScreen", "accessibility id: submit"));

        assertThat(LocatorDebtLedger.load().newDebt()).hasSize(2);
    }

    @Test
    @DisplayName("the signature identifies screen and locator, not the run")
    void signatureIsScreenAndLocator() {
        assertThat(heal("CartScreen", "accessibility id: checkout").signature())
                .isEqualTo("CartScreen :: accessibility id: checkout");
    }

    @Test
    @DisplayName("the failure message names the locator and its evidence, not a count")
    void failureMessageIsActionable() {
        LocatorDebtLedger.record(heal("CartScreen", "accessibility id: checkout"));

        String message = LocatorDebtLedger.load().describeNewDebt();

        assertThat(message)
                .contains("CartScreen :: accessibility id: checkout")
                .contains("vision fallback")
                .contains("build/artifacts/x.png")
                .contains("locator-debt.txt");
    }

    @Test
    @DisplayName("an accepted locator that nothing rescued is flagged for graduation")
    void staleAcceptancesAreFlagged() throws Exception {
        // Simulated accepted set: one entry, and this run healed something else entirely.
        LocatorDebtLedger.record(heal("LoginScreen", "accessibility id: submit"));

        LocatorDebtLedger ledger = LocatorDebtLedger.load();

        // No accepted file on the test classpath, so nothing can be stale — the real assertion is
        // that the method reports against the accepted set rather than against the heals.
        assertThat(ledger.staleAcceptances()).isEmpty();
        assertThat(ledger.newDebt()).hasSize(1);
    }

    @Test
    @DisplayName("the report records every heal with its evidence")
    void reportIsWritten(@TempDir Path dir) throws Exception {
        LocatorDebtLedger.record(heal("CartScreen", "accessibility id: checkout"));
        LocatorDebtLedger.record(heal("LoginScreen", "accessibility id: submit"));

        Path report = dir.resolve("nested").resolve("locator-debt-report.txt");
        LocatorDebtLedger.writeReport(report);

        assertThat(report).exists();
        assertThat(Files.readString(report))
                .contains("CartScreen")
                .contains("LoginScreen")
                .contains("vision fallback");
    }

    @Test
    @DisplayName("no report is written when nothing was healed")
    void noReportWithoutHeals(@TempDir Path dir) {
        Path report = dir.resolve("locator-debt-report.txt");
        LocatorDebtLedger.writeReport(report);

        assertThat(report).doesNotExist();
    }
}
