package com.example.poetryapi.poetry.application;

public class PoemGenerationUnavailableException extends PoemGenerationException {
    public PoemGenerationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
