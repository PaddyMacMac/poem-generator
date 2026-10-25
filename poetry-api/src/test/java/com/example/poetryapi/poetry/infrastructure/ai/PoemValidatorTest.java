package com.example.poetryapi.poetry.infrastructure.ai;

import com.example.poetryapi.poetry.application.PoemGenerationException;
import com.example.poetryapi.poetry.domain.Poem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoemValidatorTest {

    @Test
    void requireNonBlankTrimsValue() {
        assertThat(PoemValidator.requireNonBlank("  haiku  ", "genre")).isEqualTo("haiku");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void requireNonBlankRejectsBlankValues(String value) {
        assertThatThrownBy(() -> PoemValidator.requireNonBlank(value, "genre"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("genre must not be blank.");
    }

    @Test
    void validateTrimsTextAndUsesRequestedGenreAndTheme() {
        var poem = PoemValidator.validate(new Poem(" Title ", " Body ", "x", "y"), "haiku", "testing");

        assertThat(poem).isEqualTo(new Poem("Title", "Body", "haiku", "testing"));
    }

    @Test
    void validateRejectsNullPoem() {
        assertThatThrownBy(() -> PoemValidator.validate(null, "haiku", "testing"))
                .isInstanceOf(PoemGenerationException.class);
    }

    @Test
    void isIncompleteDetectsMissingParts() {
        assertThat(PoemValidator.isIncomplete(null)).isTrue();
        assertThat(PoemValidator.isIncomplete(new Poem(null, "c", null, null))).isTrue();
        assertThat(PoemValidator.isIncomplete(new Poem("t", " ", null, null))).isTrue();
        assertThat(PoemValidator.isIncomplete(new Poem("t", "c", null, null))).isFalse();
    }
}
