package dev.diegonava.mobile.core.junit;

import dev.diegonava.mobile.core.config.MobilePlatform;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Restricts a test to specific platforms.
 *
 * <p>Use sparingly. The default expectation in this framework is that a test runs everywhere; an
 * annotation here is an admission that the behaviour genuinely differs, and the {@code reason}
 * is what makes that reviewable. "iOS has no back button" is a reason. "Flaky on Android" is not —
 * that is what quarantine is for.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(PlatformCondition.class)
public @interface EnabledOnPlatform {

    MobilePlatform[] value();

    String reason();
}
