package com.southwestasiafloat.backend.domain.gateway;

public interface OcrGateway {
    String analyzeImage(byte[] imageBytes);
}
