package com.southwestasiafloat.backend.controller;

import com.southwestasiafloat.backend.application.service.DraftApplicationService;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/arena")
public class ArenaController {

    private final DraftApplicationService draftApplicationService;

    public ArenaController(DraftApplicationService draftApplicationService) {
        this.draftApplicationService = draftApplicationService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftAnalyzeResponse analyze(@RequestPart("file") MultipartFile file) throws Exception {
        return draftApplicationService.analyze(file);
    }
}
