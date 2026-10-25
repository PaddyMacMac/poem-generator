package com.example.poetryapi.poetry.infrastructure.ai;

import com.example.poetryapi.poetry.application.PoemGenerationException;
import com.example.poetryapi.poetry.application.PoemGenerationUnavailableException;
import com.example.poetryapi.poetry.domain.Poem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import static org.springframework.ai.chat.client.ChatClient.CallResponseSpec;

class GoogleGenAiPoemGeneratorTest {

    private final ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
    private final CallResponseSpec responseSpec = mock(CallResponseSpec.class);

    private GoogleGenAiPoemGenerator poemGenerator;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        poemGenerator = new GoogleGenAiPoemGenerator(chatClientBuilder);
    }

    @Test
    void returnsGeneratedPoemWithRequestedGenreAndTheme() {
        when(responseSpec.entity(Poem.class))
                .thenReturn(new Poem("A Small Fix", "One test turns green", "ignored", "ignored"));

        var poem = poemGenerator.generate("haiku", "testing");

        assertThat(poem).isEqualTo(new Poem("A Small Fix", "One test turns green", "haiku", "testing"));
    }

    @Test
    void buildsPromptFromTrimmedGenreAndTheme() {
        when(responseSpec.entity(Poem.class)).thenReturn(new Poem("T", "C", null, null));

        var poem = poemGenerator.generate("  haiku ", " testing  ");

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(captor.capture());
        assertThat(captor.getValue().getContents()).contains("haiku haiku about testing");
        assertThat(poem.genre()).isEqualTo("haiku");
        assertThat(poem.theme()).isEqualTo("testing");
    }

    @Test
    void rejectsBlankInputsWithoutCallingProvider() {
        assertThatThrownBy(() -> poemGenerator.generate(" ", "testing"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> poemGenerator.generate("haiku", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(requestSpec);
    }

    @Test
    void rejectsNullProviderOutput() {
        when(responseSpec.entity(Poem.class)).thenReturn(null);

        assertThatThrownBy(() -> poemGenerator.generate("haiku", "testing"))
                .isInstanceOf(PoemGenerationException.class);
    }

    @Test
    void mapsTransientProviderFailuresToUnavailableException() {
        when(requestSpec.call()).thenThrow(new TransientAiException("temporarily unavailable"));

        assertThatThrownBy(() -> poemGenerator.generate("haiku", "testing"))
                .isExactlyInstanceOf(PoemGenerationUnavailableException.class);
    }

    @Test
    void mapsNonTransientProviderFailuresToGenerationException() {
        when(requestSpec.call()).thenThrow(new NonTransientAiException("provider rejected request"));

        assertThatThrownBy(() -> poemGenerator.generate("haiku", "testing"))
                .isExactlyInstanceOf(PoemGenerationException.class);
    }

    @Test
    void rejectsIncompleteProviderOutput() {
        when(responseSpec.entity(Poem.class)).thenReturn(new Poem(" ", "content", null, null));

        assertThatThrownBy(() -> poemGenerator.generate("haiku", "testing"))
                .isInstanceOf(PoemGenerationException.class);
    }
}
