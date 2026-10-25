package com.example.poetryapi.poetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real controller, generator, validator and exception handler; only the AI model is mocked. */
@SpringBootTest(properties = "GEMINI_API_KEY=test-key")
@AutoConfigureMockMvc
class PoetryApiIntegrationTest {

    private static final String REQUEST = """
            {"genre":"haiku", "theme":"testing"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void stubDefaultOptions() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void generatesPoemThroughFullApplicationStack() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"title":" A Small Fix ","content":"One test turns green"}
                """));

        mockMvc.perform(post("/poems").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A Small Fix"))
                .andExpect(jsonPath("$.content").value("One test turns green"))
                .andExpect(jsonPath("$.genre").value("haiku"))
                .andExpect(jsonPath("$.theme").value("testing"));
    }

    @Test
    void returnsBadGatewayWhenModelReturnsIncompletePoem() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"title":"","content":"One test turns green"}
                """));

        mockMvc.perform(post("/poems").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadGateway());
    }

    @Test
    void returnsServiceUnavailableWhenModelIsTransientlyUnavailable() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new TransientAiException("down"));

        mockMvc.perform(post("/poems").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable());
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
