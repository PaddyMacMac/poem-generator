package com.example.poetryapi.poetry.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PoemGenerationRequest(
        @NotBlank @Size(max = 50) String genre,
        @NotBlank @Size(max = 500) String theme) {
}