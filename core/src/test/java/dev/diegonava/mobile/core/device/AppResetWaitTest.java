package dev.diegonava.mobile.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriverException;

/**
 * The polling half of {@code AppLifecycle.resetToCleanState()}, which is the only half a machine
 * with no device can check — and the half that already cost a CI run.
 *
 * <p>The reset removes and reinstalls the app so a reused session starts each test with no history.
 * Its first version then activated the app immediately, and iOS answered
 * {@code Application "…" is unknown to FrontBoard}: {@code installApp} returns before the platform
 * has finished making the app launchable. The gap is small, real, and not something the caller can
 * be told about — it can only be waited for.
 */
class AppResetWaitTest {

    private static final Duration LONG_ENOUGH = Duration.ofSeconds(5);

    @Test
    void returns_as_soon_as_the_condition_holds() {
        AtomicInteger calls = new AtomicInteger();

        AppLifecycle.waitFor(LONG_ENOUGH, "an immediate condition", () -> {
            calls.incrementAndGet();
            return true;
        });

        assertThat(calls).hasValue(1);
    }

    @Test
    void keeps_polling_while_the_condition_is_merely_false() {
        AtomicInteger calls = new AtomicInteger();

        AppLifecycle.waitFor(LONG_ENOUGH, "a slow install", () -> calls.incrementAndGet() >= 3);

        assertThat(calls).hasValue(3);
    }

    @Test
    void treats_a_driver_exception_as_not_yet_rather_than_as_failure() {
        // The activate case. Appium throws while the app is not yet launchable, and that throw is
        // the normal path, not an error — retrying is the entire point.
        AtomicInteger calls = new AtomicInteger();

        assertThatCode(() -> AppLifecycle.waitFor(LONG_ENOUGH, "an app becoming launchable", () -> {
                    if (calls.incrementAndGet() < 3) {
                        throw new WebDriverException("unknown to FrontBoard");
                    }
                    return true;
                }))
                .doesNotThrowAnyException();

        assertThat(calls).hasValue(3);
    }

    @Test
    void gives_up_with_a_message_naming_what_it_waited_for_and_why_it_matters() {
        assertThatThrownBy(() -> AppLifecycle.waitFor(Duration.ofMillis(600), "the app to be installed", () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the app to be installed")
                // The device is mid-reset when this fires: the app has been removed and its
                // replacement is not usable. Saying so is the difference between diagnosing this
                // and diagnosing whichever test runs next.
                .hasMessageContaining("removed and reinstalled");
    }

    @Test
    void surfaces_the_last_driver_exception_as_the_cause_when_it_gives_up() {
        // Without this the timeout says only "timed out", and the reason the platform kept
        // refusing — which is the actual diagnosis — is discarded.
        assertThatThrownBy(() -> AppLifecycle.waitFor(Duration.ofMillis(600), "a doomed activate", () -> {
                    throw new WebDriverException("unknown to FrontBoard");
                }))
                .isInstanceOf(IllegalStateException.class)
                .cause()
                .isInstanceOf(WebDriverException.class)
                .hasMessageContaining("unknown to FrontBoard");
    }
}
