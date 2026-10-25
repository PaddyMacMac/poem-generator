package com.example.poetryapi.poetry.web;

import com.example.poetryapi.poetry.application.PoemGenerationException;
import com.example.poetryapi.poetry.application.PoemGenerationUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PoetryExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(PoetryExceptionHandler.class);

    @ExceptionHandler(PoemGenerationException.class)
    public ProblemDetail handlePoemGenerationException(PoemGenerationException exception) {
        logger.error("Poem generation failed because of an AI provider error.", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Unable to generate a poem right now. Please try again later.");
    }

    @ExceptionHandler(PoemGenerationUnavailableException.class)
    public ProblemDetail handlePoemGenerationUnavailableException(
            PoemGenerationUnavailableException exception) {
        logger.error("Poem generation failed because the AI provider is unavailable.", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Poem generation is temporarily unavailable. Please try again later.");
    }
}