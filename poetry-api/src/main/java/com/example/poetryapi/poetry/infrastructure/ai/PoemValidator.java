package com.example.poetryapi.poetry.infrastructure.ai;

import com.example.poetryapi.poetry.application.PoemGenerationException;
import com.example.poetryapi.poetry.domain.Poem;

final class PoemValidator {

    private PoemValidator() {
    }

    static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value.trim();
    }

    static Poem validate(Poem generatedPoem, String genre, String theme) {
        if (isIncomplete(generatedPoem)) {
            throw new PoemGenerationException("The AI provider returned an incomplete poem.");
        }

        return new Poem(normalize(generatedPoem.title()), normalize(generatedPoem.content()), genre, theme);
    }

    static boolean isIncomplete(Poem generatedPoem) {
        return generatedPoem == null || isBlank(generatedPoem.title()) || isBlank(generatedPoem.content());
    }

    private static String normalize(String text) {
        return text == null ? null : text.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
