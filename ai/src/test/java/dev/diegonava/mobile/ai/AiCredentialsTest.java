package dev.diegonava.mobile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiCredentialsTest {

    private static final String SECRET = "sk-ant-not-a-real-key-0123456789";

    private static UnaryOperator<String> from(Map<String, String> values) {
        return values::get;
    }

    private static final UnaryOperator<String> NOTHING = from(Map.of());

    @Test
    @DisplayName("no key anywhere is an ordinary outcome, not an error")
    void absentIsFine() {
        AiCredentials credentials = AiCredentials.resolve(NOTHING, NOTHING);

        assertThat(credentials.isPresent()).isFalse();
    }

    @Test
    @DisplayName("the environment variable is read")
    void readsEnvironment() {
        AiCredentials credentials = AiCredentials.resolve(NOTHING, from(Map.of(AiCredentials.ENV_KEY, SECRET)));

        assertThat(credentials.isPresent()).isTrue();
        assertThat(credentials.require()).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("a system property outranks the environment, matching FrameworkConfig's order")
    void propertyWins() {
        AiCredentials credentials = AiCredentials.resolve(
                from(Map.of(AiCredentials.PROPERTY_KEY, "from-property")),
                from(Map.of(AiCredentials.ENV_KEY, "from-environment")));

        assertThat(credentials.require()).isEqualTo("from-property");
    }

    @Test
    @DisplayName("a blank value counts as absent")
    void blankIsAbsent() {
        assertThat(AiCredentials.resolve(NOTHING, from(Map.of(AiCredentials.ENV_KEY, "   ")))
                        .isPresent())
                .isFalse();
    }

    @Test
    @DisplayName("whitespace around a key is stripped, because copy-paste adds it")
    void keyIsStripped() {
        assertThat(AiCredentials.resolve(NOTHING, from(Map.of(AiCredentials.ENV_KEY, "  " + SECRET + "\n")))
                        .require())
                .isEqualTo(SECRET);
    }

    @Test
    @DisplayName("require() explains itself rather than throwing a bare NPE downstream")
    void requireExplains() {
        assertThatThrownBy(() -> AiCredentials.resolve(NOTHING, NOTHING).require())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AiCredentials.ENV_KEY)
                .hasMessageContaining("isPresent()");
    }

    @Test
    @DisplayName("the credential never prints its key")
    void toStringIsRedacted() {
        // This is why AiCredentials is not a record. Secrets reach CI logs through incidental
        // toString calls — a logged config object, an exception message, a debugger — far more
        // often than through anyone deliberately printing them.
        AiCredentials credentials = AiCredentials.resolve(NOTHING, from(Map.of(AiCredentials.ENV_KEY, SECRET)));

        assertThat(credentials.toString()).doesNotContain(SECRET).contains("present");
    }
}
