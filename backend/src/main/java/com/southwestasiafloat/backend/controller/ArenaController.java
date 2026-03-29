package com.southwestasiafloat.backend.controller;

import com.southwestasiafloat.backend.api.request.ArenaAnalyzeRequest;
import com.southwestasiafloat.backend.api.response.ArenaAnalyzeResponse;
import com.southwestasiafloat.backend.dto.request.DraftAnalyzeRequest;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arena")
public class ArenaController {

    private final ArenaApplicationService arenaApplicationService;

    public ArenaController(ArenaApplicationService arenaApplicationService) {
        this.arenaApplicationService = arenaApplicationService;
    }

    @PostMapping("/analyze")
    public DraftAnalyzeResponse analyze(@RequestBody DraftAnalyzeRequest request) {
        ArenaAnalyzeResponse response = arenaApplicationService.analyze(
                new ArenaAnalyzeRequest(request.screenshotBase64(), request.cards())
        );
        return new DraftAnalyzeResponse(response.recognizedText(), response.suggestions());
    }
}
