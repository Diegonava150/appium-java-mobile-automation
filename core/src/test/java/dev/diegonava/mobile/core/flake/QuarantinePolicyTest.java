package dev.diegonava.mobile.core.flake;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The quarantine deadline is the whole point of the mechanism, so it gets tested directly rather
 * than only through a live retry.
 */
class QuarantinePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    @Test
    @DisplayName("a quarantine still inside its window is allowed")
    void futureDeadlineIsFine() {
        assertThatCode(() -> QuarantinePolicy.assertNotExpired(
                        "SomeTest.someMethod", "the list animates in", "2026-09-30", "", TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the deadline day itself is still inside the window")
    void deadlineDayIsInclusive() {
        assertThatCode(() -> QuarantinePolicy.assertNotExpired(
                        "SomeTest.someMethod", "the list animates in", "2026-08-10", "", TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an expired quarantine fails, and the message says what to do about it")
    void expiredDeadlineFails() {
        assertThatThrownBy(() -> QuarantinePolicy.assertNotExpired(
                        "CartTest.removesItem", "the remove animation races the assertion", "2026-08-09", "", TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Quarantine expired for CartTest.removesItem")
                .hasMessageContaining("2026-08-09")
                .hasMessageContaining("the remove animation races the assertion")
                .hasMessageContaining("remove @Flaky");
    }

    @Test
    @DisplayName("a tracking issue is surfaced in the expiry message when one was recorded")
    void issueIsIncludedWhenPresent() {
        assertThatThrownBy(() -> QuarantinePolicy.assertNotExpired(
                        "CartTest.removesItem", "races the assertion", "2026-01-01", "PROJ-1234", TODAY))
                .hasMessageContaining("PROJ-1234");
    }

    @Test
    @DisplayName("a malformed deadline is rejected rather than silently ignored")
    void malformedDeadlineIsRejected() {
        assertThatThrownBy(() ->
                        QuarantinePolicy.assertNotExpired("SomeTest.someMethod", "reason", "next tuesday", "", TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an ISO-8601 date");
    }
}
