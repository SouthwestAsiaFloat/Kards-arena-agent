package com.southwestasiafloat.backend.domain.gateway;

import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;

import java.util.Optional;

public interface AnalyzeResultCache {

    Optional<DraftAnalyzeResponse> get(String key);

    void put(String key, DraftAnalyzeResponse response);
}
