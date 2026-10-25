package com.example.poetryapi.poetry.web;

import com.example.poetryapi.poetry.application.PoemGenerator;
import com.example.poetryapi.poetry.domain.Poem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PoetryController {

    private final PoemGenerator poemGenerator;

    public PoetryController(PoemGenerator poemGenerator) {
        this.poemGenerator = poemGenerator;
    }

    @PostMapping("/poems")
    public ResponseEntity<Poem> generate(@Valid @RequestBody PoemGenerationRequest request) {
        var response = poemGenerator.generate(request.genre(), request.theme());
        return ResponseEntity.ok(response);
    }
}