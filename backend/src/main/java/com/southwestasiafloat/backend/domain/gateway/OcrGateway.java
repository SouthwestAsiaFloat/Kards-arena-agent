package com.southwestasiafloat.backend.domain.gateway;

// OCR service gateway contract. The backend keeps payload parsing in application layer for now.
public interface OcrGateway {

    String analyzeImage(String imageBase64);
}
