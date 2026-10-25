package com.example.poetryapi.poetry.application;

public class PoemGenerationException extends RuntimeException {
    public PoemGenerationException(String message) {
        super(message);
    }

    public PoemGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}