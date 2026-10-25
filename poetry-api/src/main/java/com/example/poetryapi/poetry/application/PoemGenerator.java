package com.example.poetryapi.poetry.application;

import com.example.poetryapi.poetry.domain.Poem;

@FunctionalInterface
public interface PoemGenerator {
    Poem generate(String genre, String theme);
}