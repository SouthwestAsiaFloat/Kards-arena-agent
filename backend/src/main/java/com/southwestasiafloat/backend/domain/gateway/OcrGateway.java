package com.southwestasiafloat.backend.domain.gateway;

/**
 * OCR 网关接口。
 */

public interface OcrGateway {
    String analyzeImage(byte[] imageBytes);
}
