package dev.diegonava.mobile.core.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test as known-flaky: it will be retried, and its instability is recorded as debt.
 *
 * <p>Replaces {@code @Test}, it does not accompany it.
 *
 * <p>The usual quarantine is a place tests go to die. A test gets tagged, the retry hides the
 * problem, and three years later nobody knows whether the underlying bug was ever real. This
 * annotation is built so that cannot happen:
 *
 * <ul>
 *   <li>{@link #reason} is mandatory. "Flaky" is not a reason; "the catalog animates in and the
 *       first tap lands before the list settles" is.
 *   <li>{@link #expires} is mandatory and is a hard deadline. Once that date passes the test
 *       <b>fails immediately, without running</b>. Quarantine is a loan with a due date, and this
 *       is the repayment being enforced rather than requested.
 *   <li>Every attempt is recorded to the flake ledger, so the retry buys a green build and still
 *       leaves a paper trail saying what it cost.
 * </ul>
 *
 * <p>A retried test gets a completely fresh device session per attempt, because the driver
 * lifecycle is per-invocation. A retry is therefore a genuine re-run, not a second poke at an
 * app that is already in a bad state.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(FlakyExtension.class)
public @interface Flaky {

    /** Total attempts before the test is reported as failed. */
    int maxAttempts() default 3;

    /** Why this test is unstable. Describe the mechanism, not the symptom. */
    String reason();

    /**
     * ISO-8601 date ({@code 2026-09-30}) after which this test fails without running.
     *
     * <p>Keep it short. A quarantine period longer than a sprint is an admission that nobody
     * intends to fix it, and in that case deleting the test is the more honest option.
     */
    String expires();

    /** Optional tracking issue. */
    String issue() default "";
}
