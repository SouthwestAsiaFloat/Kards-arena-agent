package com.southwestasiafloat.backend.controller;

import com.southwestasiafloat.backend.application.service.DraftApplicationService;
import com.southwestasiafloat.backend.application.service.DraftSessionApplicationService;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.dto.request.DraftPickRequest;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import com.southwestasiafloat.backend.dto.response.StartDraftResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/arena")
public class ArenaController {

    private final DraftApplicationService draftApplicationService;
    private final DraftSessionApplicationService draftSessionApplicationService;

    public ArenaController(DraftApplicationService draftApplicationService,
                           DraftSessionApplicationService draftSessionApplicationService) {
        this.draftApplicationService = draftApplicationService;
        this.draftSessionApplicationService = draftSessionApplicationService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftAnalyzeResponse analyze(@RequestPart("file") MultipartFile file) throws Exception {
        return draftApplicationService.analyze(file);
    }

    /**
     * 开始一局 draft
     */
    @PostMapping("/start")
    public StartDraftResponse startDraft() {
        String sessionId = draftSessionApplicationService.createSession().getSessionId();
        return new StartDraftResponse(sessionId, "Draft started successfully");
    }

    /**
     * 获取当前 session 状态
     */
    @GetMapping("/session/{sessionId}")
    public DraftSession getSession(@PathVariable String sessionId) {
        return draftSessionApplicationService.getSession(sessionId);
    }

    /**
     * 用户确认抓牌
     */
    @PostMapping("/pick")
    public DraftSession pickCard(@RequestBody DraftPickRequest request) {
        return draftSessionApplicationService.pickCard(
                request.getSessionId(),
                request.getPickedCard()
        );
    }

    /**
     * 结束一局
     */
    @DeleteMapping("/session/{sessionId}")
    public String endDraft(@PathVariable String sessionId) {
        draftSessionApplicationService.removeSession(sessionId);
        return "Draft session ended: " + sessionId;
    }

}
