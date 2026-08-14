package dev.diegonava.mobile.core.junit;

import dev.diegonava.mobile.core.flake.FlakeLedger;
import dev.diegonava.mobile.core.flake.FlakeRecord;
import dev.diegonava.mobile.core.flake.QuarantinePolicy;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements {@link Flaky}: bounded retries, with every attempt recorded.
 *
 * <p>Built directly on the Jupiter extension model rather than taking a dependency on
 * {@code junit-pioneer}, which still targets JUnit 5 (see ADR-005). Retries are generated lazily
 * by a spliterator that stops the moment the test passes, so a stable run costs exactly one
 * invocation.
 *
 * <p>Intermediate failures are converted to {@link TestAbortedException} rather than swallowed.
 * That matters for honesty in the report: a retried attempt shows up as <i>aborted</i>, not as
 * passed, so the run's own output shows the instability instead of hiding it.
 */
public final class FlakyExtension implements TestTemplateInvocationContextProvider {

    private static final Logger log = LoggerFactory.getLogger(FlakyExtension.class);

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> method.isAnnotationPresent(Flaky.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        Method method = context.getRequiredTestMethod();
        Flaky flaky = method.getAnnotation(Flaky.class);
        String testId = context.getRequiredTestClass().getSimpleName() + "." + method.getName();

        // Before anything runs. An expired quarantine is a build failure, not a test result.
        QuarantinePolicy.assertNotExpired(testId, flaky.reason(), flaky.expires(), flaky.issue(), LocalDate.now());

        RetryState state = new RetryState(testId, context.getDisplayName(), flaky);
        return StreamSupport.stream(new RetrySpliterator(state), false);
    }

    // ------------------------------------------------------------------ state

    private static final class RetryState {
        private final String testId;
        private final String displayName;
        private final Flaky flaky;
        private final List<String> failures = new ArrayList<>();

        private int attempts;
        private boolean passed;

        private RetryState(String testId, String displayName, Flaky flaky) {
            this.testId = testId;
            this.displayName = displayName;
            this.flaky = flaky;
        }

        synchronized void recordFailure(Throwable throwable) {
            attempts++;
            failures.add(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        synchronized void recordSuccess() {
            attempts++;
            passed = true;
        }

        synchronized boolean hasRemainingAttempts() {
            return attempts < flaky.maxAttempts();
        }

        synchronized boolean isComplete() {
            return passed || attempts >= flaky.maxAttempts();
        }

        synchronized void publish() {
            FlakeLedger.record(new FlakeRecord(
                    testId,
                    displayName,
                    attempts,
                    passed,
                    passed && attempts == 1,
                    flaky.reason(),
                    flaky.expires(),
                    flaky.issue(),
                    List.copyOf(failures)));

            if (passed && attempts == 1) {
                log.info(
                        "{} passed first try while quarantined. If that holds, remove @Flaky "
                                + "and let it stand on its own.",
                        testId);
            } else if (passed) {
                log.warn("{} passed only on attempt {} of {}", testId, attempts, flaky.maxAttempts());
            } else {
                log.error("{} failed all {} attempts", testId, attempts);
            }
        }
    }

    // ------------------------------------------------------------ invocations

    private static final class RetrySpliterator
            extends Spliterators.AbstractSpliterator<TestTemplateInvocationContext> {

        private final RetryState state;

        private RetrySpliterator(RetryState state) {
            super(Long.MAX_VALUE, Spliterator.NONNULL);
            this.state = state;
        }

        @Override
        public boolean tryAdvance(Consumer<? super TestTemplateInvocationContext> action) {
            if (state.isComplete()) {
                // JUnit has stopped asking for invocations, so the outcome is settled.
                state.publish();
                return false;
            }
            action.accept(new RetryInvocationContext(state));
            return true;
        }
    }

    private record RetryInvocationContext(RetryState state) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return "%s [attempt %d of %d]".formatted(state.displayName, invocationIndex, state.flaky.maxAttempts());
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new RetryOutcomeRecorder(state));
        }
    }

    /**
     * Turns a failed attempt into an abort so the next one runs.
     *
     * <p>Implements <b>both</b> exception-handler interfaces, and the second one is not optional.
     * {@link TestExecutionExceptionHandler} covers exceptions thrown by the test body and nothing
     * else; a failure inside a {@code BeforeEachCallback} goes to
     * {@link LifecycleMethodExecutionExceptionHandler} instead. Handling only the first meant this
     * extension retried the failures a mobile suite rarely has and ignored the one it has most
     * often — {@code DriverExtension} failing to open a session.
     *
     * <p>That gap was live in CI and read as something else entirely. The iOS lane reported
     * {@code [attempt 1 of 3] FAILED} followed by {@code [attempt 2 of 3] PASSED}, and then failed
     * the build: the retry had worked, but attempt 1's exception was never converted, so JUnit
     * recorded a genuine failure. The suite was doing the right thing and reporting the wrong one.
     */
    private record RetryOutcomeRecorder(RetryState state)
            implements TestExecutionExceptionHandler,
                    LifecycleMethodExecutionExceptionHandler,
                    AfterTestExecutionCallback {

        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
            throw abortOrFail(throwable);
        }

        /**
         * A session that would not start is the canonical retryable failure on a device suite —
         * the emulator was busy, the simulator was still booting, WebDriverAgent lost a race. It
         * says nothing about the test, which is exactly what a retry is for.
         */
        @Override
        public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable)
                throws Throwable {
            throw abortOrFail(throwable);
        }

        private Throwable abortOrFail(Throwable throwable) {
            state.recordFailure(throwable);

            if (state.hasRemainingAttempts()) {
                return new TestAbortedException(
                        "Attempt %d failed, retrying: %s".formatted(state.attempts, throwable.getMessage()));
            }
            return throwable;
        }

        @Override
        public void afterTestExecution(ExtensionContext context) {
            if (context.getExecutionException().isEmpty()) {
                state.recordSuccess();
            }
        }
    }
}
