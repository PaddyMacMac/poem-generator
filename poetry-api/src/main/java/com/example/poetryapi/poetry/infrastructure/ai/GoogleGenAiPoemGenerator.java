package com.example.poetryapi.poetry.infrastructure.ai;

import com.example.poetryapi.poetry.application.PoemGenerationException;
import com.example.poetryapi.poetry.application.PoemGenerationUnavailableException;
import com.example.poetryapi.poetry.application.PoemGenerator;
import com.example.poetryapi.poetry.domain.Poem;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service
public final class GoogleGenAiPoemGenerator implements PoemGenerator {

    private static final PromptTemplate PROMPT_TEMPLATE = new PromptTemplate(
            "Write a {genre} haiku about {theme} following the traditional 5-7-5 syllable structure.");

    private final ChatClient chatClient;

    public GoogleGenAiPoemGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Poem generate(String genre, String theme) {
        var normalizedGenre = PoemValidator.requireNonBlank(genre, "genre");
        var normalizedTheme = PoemValidator.requireNonBlank(theme, "theme");

        var prompt = buildPrompt(normalizedGenre, normalizedTheme);

        try {
            var generatedPoem = chatClient.prompt(prompt).call().entity(Poem.class);
            return PoemValidator.validate(generatedPoem, normalizedGenre, normalizedTheme);
        } catch (TransientAiException exception) {
            throw new PoemGenerationUnavailableException("The AI provider is temporarily unavailable.", exception);
        } catch (NonTransientAiException exception) {
            throw new PoemGenerationException("The AI provider could not generate a poem.", exception);
        }
    }

    private Prompt buildPrompt(String normalizedGenre, String normalizedTheme) {
        return PROMPT_TEMPLATE.create(Map.of("genre", normalizedGenre, "theme", normalizedTheme));
    }
}