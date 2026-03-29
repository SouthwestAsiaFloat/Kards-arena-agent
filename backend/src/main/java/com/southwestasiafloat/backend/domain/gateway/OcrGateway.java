package com.southwestasiafloat.backend.domain.gateway;

public interface OcrGateway {

    String extractTextFromImage(String imageBase64);
}

