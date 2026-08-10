package dev.diegonava.mobile.core.flake;

import java.util.List;

/**
 * What one quarantined test cost during a run.
 *
 * @param testId stable identifier, {@code ClassName.methodName}
 * @param displayName the human-readable test name
 * @param attempts how many attempts were made
 * @param passed whether it eventually passed
 * @param passedFirstAttempt whether it passed without needing a retry — the graduation signal
 * @param reason the justification recorded on the annotation
 * @param expires the quarantine deadline
 * @param issue optional tracking issue
 * @param failures one message per failed attempt
 */
public record FlakeRecord(
        String testId,
        String displayName,
        int attempts,
        boolean passed,
        boolean passedFirstAttempt,
        String reason,
        String expires,
        String issue,
        List<String> failures) {}
