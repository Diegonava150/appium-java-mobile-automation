package dev.diegonava.mobile.core.flake;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Enforces the terms of a quarantine. Separated from the JUnit plumbing so it is directly testable. */
public final class QuarantinePolicy {

    private QuarantinePolicy() {}

    /**
     * Fails if the quarantine deadline has passed.
     *
     * <p>This is the inversion that makes the whole mechanism work. A conventional quarantine
     * decays silently: the retry keeps the build green, so nothing ever forces the conversation
     * about the underlying bug. Here the deadline turns the debt into something that eventually
     * breaks the build on purpose, at a date somebody chose, with the original reason attached to
     * the failure message.
     *
     * @throws IllegalStateException on an expired or unparseable deadline
     */
    public static void assertNotExpired(String testId, String reason, String expires, String issue, LocalDate today) {
        LocalDate deadline;
        try {
            deadline = LocalDate.parse(expires.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    "@Flaky on %s has expires=\"%s\", which is not an ISO-8601 date such as 2026-09-30."
                            .formatted(testId, expires),
                    e);
        }

        if (today.isAfter(deadline)) {
            throw new IllegalStateException("""
                    Quarantine expired for %s (deadline was %s, today is %s).

                    Recorded reason: %s%s

                    This test has been retried on every run since it was quarantined, and the \
                    deadline for dealing with that has passed. Pick one:
                      - fix the underlying instability and remove @Flaky
                      - delete the test, if it is no longer earning its keep
                      - extend expires, and say in the pull request why the extension is justified
                    """.formatted(
                    testId, deadline, today, reason, issue == null || issue.isBlank() ? "" : " (see " + issue + ")"));
        }
    }
}
