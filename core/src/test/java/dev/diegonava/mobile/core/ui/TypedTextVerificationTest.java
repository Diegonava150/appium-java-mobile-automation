package dev.diegonava.mobile.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The predicate behind {@code BaseScreen.type}'s read-back check.
 *
 * <p>It exists because {@code sendKeys} into a React Native field on iOS silently drops characters.
 * A sign-in typed {@code bb@example.com} for {@code bob@example.com} and left the password field
 * empty; the app answered "Provided credentials do not match any user in this service", and the
 * suite reported "CatalogScreen did not appear within 20s" twenty seconds later. That failure had
 * been filed as an infrastructure flake more than once.
 *
 * <p>It is also the riskiest part of the fix. A predicate that is too strict turns passing tests
 * red for no reason, on a lane that takes half an hour to say so — so the cases where it must
 * <em>not</em> complain matter as much as the ones where it must.
 */
class TypedTextVerificationTest {

    @Test
    @DisplayName("a field that echoes exactly what was typed is accepted")
    void exact_match() {
        assertThat(BaseScreen.arrivedIntact("bob@example.com", "bob@example.com"))
                .isTrue();
    }

    @Test
    @DisplayName("the observed corruption is rejected")
    void the_bug_this_exists_for() {
        assertThat(BaseScreen.arrivedIntact("bob@example.com", "bb@example.com"))
                .isFalse();
    }

    @Test
    @DisplayName("a secure field is judged on length, since its characters are not readable")
    void masked_field_of_the_right_length() {
        assertThat(BaseScreen.arrivedIntact("10203040", "••••••••")).isTrue();
        assertThat(BaseScreen.arrivedIntact("10203040", "********")).isTrue();
    }

    @Test
    @DisplayName("a secure field holding the wrong number of characters is rejected")
    void masked_field_of_the_wrong_length() {
        assertThat(BaseScreen.arrivedIntact("10203040", "•••")).isFalse();
    }

    @Test
    @DisplayName("an empty field after typing a password is the failure, not an unreadable one")
    void empty_after_typing() {
        // Exactly what the screenshot showed. Accepting this would reinstate the bug in the one
        // shape it actually took.
        assertThat(BaseScreen.arrivedIntact("10203040", "")).isFalse();
    }

    @Test
    @DisplayName("a field that reports nothing at all is not judged")
    void unreadable_field_is_left_alone() {
        // The safe direction to be wrong in. Before this check existed nothing was verified at all;
        // a platform that will not report a value should get that behaviour back rather than a
        // failure invented out of missing information.
        assertThat(BaseScreen.arrivedIntact("bob@example.com", null)).isTrue();
    }

    @Test
    @DisplayName("clearing a field to empty is not a failure")
    void deliberately_empty() {
        assertThat(BaseScreen.arrivedIntact("", "")).isTrue();
    }

    @Test
    @DisplayName("a field that formats a card number as it is typed is accepted")
    void card_number_auto_formatting() {
        // The regression this predicate caused on its first CI run. Four Android tests failed with
        // "Typed 16 characters ... and the field still reads 4111 1111 1111 1111" — which is the
        // field doing its job. A check that calls correct behaviour a defect is worse than no check.
        assertThat(BaseScreen.arrivedIntact("4111111111111111", "4111 1111 1111 1111"))
                .isTrue();
    }

    @Test
    @DisplayName("other separators a field supplies are accepted too")
    void other_inserted_separators() {
        assertThat(BaseScreen.arrivedIntact("1225", "12/25")).isTrue();
        assertThat(BaseScreen.arrivedIntact("5551234567", "(555) 123-4567")).isTrue();
    }

    @Test
    @DisplayName("a digit genuinely missing from a formatted field is still caught")
    void formatting_does_not_hide_a_dropped_digit() {
        // The formatting allowance must not become a blanket amnesty: strip the spaces and this
        // card is fifteen digits, not sixteen.
        assertThat(BaseScreen.arrivedIntact("4111111111111111", "4111 1111 1111 111"))
                .isFalse();
    }

    @Test
    @DisplayName("stripping separators must not blur an email into a different one")
    void separator_stripping_is_narrow() {
        // Why the strip set is spaces, hyphens, slashes and brackets rather than \W: losing "@"
        // and "." would make these two indistinguishable, and this is the exact corruption the
        // whole check exists to catch.
        assertThat(BaseScreen.arrivedIntact("bob@example.com", "bobexample.com"))
                .isFalse();
    }
}
