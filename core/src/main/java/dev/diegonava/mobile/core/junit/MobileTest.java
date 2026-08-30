package dev.diegonava.mobile.core.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Marks a class as a device-backed test.
 *
 * <p>One annotation composes the whole lifecycle so no test class ever has to remember which
 * extensions to register or in what order. Registration order matters here: the driver extension
 * comes first so it opens before, and closes after, everything wrapped inside it.
 *
 * <p>Methods within a class run in the same thread; classes run concurrently. A single device
 * cannot meaningfully interleave two test methods, so the parallelism unit is the class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("mobile")
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith({DriverExtension.class, FailureArtifactExtension.class})
public @interface MobileTest {

    /**
     * How long one Appium session lives for this class.
     *
     * <p>Defaults to a session per test, which assumes nothing. A class that opts into
     * {@link SessionScope#PER_CLASS} is asserting that its tests are isolated by the app being
     * reinstalled between them, and do not depend on the device state or the session itself being
     * fresh. Read {@link SessionScope#PER_CLASS} before setting it.
     */
    SessionScope session() default SessionScope.PER_TEST;
}
