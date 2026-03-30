package com.southwestasiafloat.backend.controller;

import com.southwestasiafloat.backend.application.service.DraftApplicationService;
import com.southwestasiafloat.backend.dto.request.DraftAnalyzeRequest;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arena")
public class ArenaController {

    private final DraftApplicationService draftApplicationService;

    public ArenaController(DraftApplicationService draftApplicationService) {
        this.draftApplicationService = draftApplicationService;
    }

    @PostMapping("/analyze")
    public DraftAnalyzeResponse analyze(@RequestBody DraftAnalyzeRequest request) {
        return draftApplicationService.analyze(request);
    }
}
