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
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * A device-backed test that starts from the <i>previous</i> release of the app.
 *
 * <p>Extension order is load-bearing. {@code UpgradeSetupExtension} is declared first so its
 * {@code beforeEach} runs before the driver opens — it installs the old build via adb and unsets
 * the {@code app} capability, so the session attaches to what is on the device rather than
 * reinstalling the current release over the top of it. Its {@code afterEach} correspondingly runs
 * last, clearing the overrides after the session has closed.
 *
 * <p>The whole class also holds an exclusive lock: installing and uninstalling packages is
 * device-global state, and doing it underneath another test running on the same device would
 * produce failures that look like anything except their actual cause.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("mobile")
@Tag("upgrade")
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("device-package-manager")
@ExtendWith({UpgradeSetupExtension.class, DriverExtension.class, FailureArtifactExtension.class})
public @interface MobileUpgradeTest {}
