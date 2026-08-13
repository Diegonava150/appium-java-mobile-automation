package dev.diegonava.mobile.ai;

import java.util.function.UnaryOperator;

/**
 * The Anthropic API key, if there is one.
 *
 * <p>Resolution order matches {@code FrameworkConfig}: the {@code anthropic.api.key} system
 * property first, then the {@code ANTHROPIC_API_KEY} environment variable. Absent is a completely
 * ordinary outcome, not an error — the whole point of the SPI in {@code core} is that a run with
 * no key behaves exactly like a run without this module.
 *
 * <p>{@link #toString()} is overridden. A record would have printed the key into the first log
 * line that mentioned it, and secrets leak into CI logs precisely through incidental
 * {@code toString} calls nobody wrote on purpose.
 */
public final class AiCredentials {

    static final String ENV_KEY = "ANTHROPIC_API_KEY";
    static final String PROPERTY_KEY = "anthropic.api.key";

    private final String apiKey;

    private AiCredentials(String apiKey) {
        this.apiKey = apiKey;
    }

    public static AiCredentials fromEnvironment() {
        return resolve(System::getProperty, System::getenv);
    }

    /** Seam for tests: the resolution rules are worth asserting, the real environment is not. */
    static AiCredentials resolve(UnaryOperator<String> properties, UnaryOperator<String> environment) {
        String fromProperty = properties.apply(PROPERTY_KEY);
        if (isSet(fromProperty)) {
            return new AiCredentials(fromProperty.strip());
        }
        String fromEnvironment = environment.apply(ENV_KEY);
        if (isSet(fromEnvironment)) {
            return new AiCredentials(fromEnvironment.strip());
        }
        return new AiCredentials(null);
    }

    public boolean isPresent() {
        return apiKey != null;
    }

    public String require() {
        if (apiKey == null) {
            throw new IllegalStateException(
                    "No Anthropic API key. Set %s or -D%s. Callers should check isPresent() first: the AI layer is optional by design."
                            .formatted(ENV_KEY, PROPERTY_KEY));
        }
        return apiKey;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return isPresent() ? "AiCredentials[present]" : "AiCredentials[absent]";
    }
}
