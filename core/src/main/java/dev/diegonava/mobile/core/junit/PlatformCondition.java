package dev.diegonava.mobile.core.junit;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.config.MobilePlatform;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/** Evaluates {@link EnabledOnPlatform} against the platform this run targets. */
public final class PlatformCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult NO_ANNOTATION =
            ConditionEvaluationResult.enabled("No @EnabledOnPlatform present");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<EnabledOnPlatform> annotation =
                AnnotationSupport.findAnnotation(context.getElement(), EnabledOnPlatform.class);

        if (annotation.isEmpty()) {
            return NO_ANNOTATION;
        }

        MobilePlatform current = FrameworkConfig.get().platform();
        MobilePlatform[] allowed = annotation.get().value();

        if (Arrays.asList(allowed).contains(current)) {
            return ConditionEvaluationResult.enabled("Enabled on " + current);
        }
        return ConditionEvaluationResult.disabled("Skipped on %s (runs on %s) — %s"
                .formatted(current, Arrays.toString(allowed), annotation.get().reason()));
    }
}
