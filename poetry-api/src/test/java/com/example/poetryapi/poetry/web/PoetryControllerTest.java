package com.example.poetryapi.poetry.web;

import com.example.poetryapi.poetry.application.PoemGenerationException;
import com.example.poetryapi.poetry.application.PoemGenerationUnavailableException;
import com.example.poetryapi.poetry.application.PoemGenerator;
import com.example.poetryapi.poetry.domain.Poem;
import com.example.poetryapi.poetry.domain.Poem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PoetryController.class)
@Import(PoetryExceptionHandler.class)
class PoetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PoemGenerator poemGenerator;

    @Test
    void returnsGeneratedPoem() throws Exception {
        when(poemGenerator.generate("haiku", "testing"))
                .thenReturn(new Poem("A Small Fix", "One test turns green", "haiku", "testing"));

        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":"haiku", "theme":"testing"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A Small Fix"))
                .andExpect(jsonPath("$.content").value("One test turns green"))
                .andExpect(jsonPath("$.genre").value("haiku"))
                .andExpect(jsonPath("$.theme").value("testing"));
    }

    @Test
    void rejectsMissingOrBlankTheme() throws Exception {
        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":"haiku"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":"haiku", "theme":""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(poemGenerator);
    }

    @Test
    void rejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(poemGenerator);
    }

    @Test
    void rejectsBlankGenerationInputs() throws Exception {
        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":" ", "theme":"code review comments"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(poemGenerator);
    }

    @Test
    void rejectsInputsExceedingMaximumLength() throws Exception {
        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":"%s", "theme":"code review comments"}
                                """.formatted("g".repeat(51))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":"haiku", "theme":"%s"}
                                """.formatted("t".repeat(501))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(poemGenerator);
    }

    @Test
    void returnsBadGatewayWhenProviderRejectsGeneration() throws Exception {
        when(poemGenerator.generate("frustrated", "code review comments"))
                .thenThrow(new PoemGenerationException("provider failure", new RuntimeException()));

        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":"frustrated", "theme":"code review comments"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail")
                        .value("Unable to generate a poem right now. Please try again later."));
    }

    @Test
    void returnsServiceUnavailableWhenProviderIsTemporarilyUnavailable() throws Exception {
        when(poemGenerator.generate("frustrated", "code review comments"))
                .thenThrow(new PoemGenerationUnavailableException(
                        "provider unavailable", new RuntimeException()));

        mockMvc.perform(post("/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre":"frustrated", "theme":"code review comments"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("Poem generation is temporarily unavailable. Please try again later."));
    }
}
