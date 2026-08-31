package dev.diegonava.mobile.core.junit.probes;

import dev.diegonava.mobile.core.junit.Flaky;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Run by {@code FlakyInvocationBoundTest} through {@code EngineTestKit}. Never run on its own —
 * this one fails every invocation deliberately, so a suite that picked it up would be red.
 *
 * <p>It throws from a {@code BeforeEachCallback}, which is the blind spot: neither of
 * {@code FlakyExtension}'s exception handlers is told, so nothing counts the attempt.
 */
@ExtendWith(BeforeEachCallbackThrowsProbe.Detonator.class)
public class BeforeEachCallbackThrowsProbe {

    /** How many attempts the retry is allowed, and therefore how many failures to expect. */
    public static final int MAX_ATTEMPTS = 3;

    /** Throws where neither of FlakyExtension's exception handlers can see it. */
    public static class Detonator implements BeforeEachCallback {
        @Override
        public void beforeEach(ExtensionContext context) {
            throw new IllegalStateException("no session for you");
        }
    }

    @Flaky(
            reason = "Not flaky. Declared so a retry is attempted at all.",
            expires = "2099-01-01",
            maxAttempts = MAX_ATTEMPTS)
    void never_gets_to_run() {}
}
