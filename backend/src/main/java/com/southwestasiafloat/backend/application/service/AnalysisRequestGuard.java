package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.config.ArenaAnalysisProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AnalysisRequestGuard {

    private final ArenaAnalysisProperties analysisProperties;
    private final AnalyzeJobStore analyzeJobStore;

    public AnalysisRequestGuard(ArenaAnalysisProperties analysisProperties,
                                AnalyzeJobStore analyzeJobStore) {
        this.analysisProperties = analysisProperties;
        this.analyzeJobStore = analyzeJobStore;
    }

    public void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded image is empty.");
        }

        long maxBytes = analysisProperties.getMaxUploadSize().toBytes();
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Uploaded image is too large.");
        }

        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.isBlank()
                && !contentType.toLowerCase().startsWith("image/")
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Uploaded file must be an image.");
        }
    }

    public void ensureAsyncCapacity(String sessionId) {
        int maxActiveJobs = analysisProperties.getMaxActiveJobs();
        if (maxActiveJobs > 0 && analyzeJobStore.countActiveJobs() >= maxActiveJobs) {
            throw new ServiceBusyException("Analyze queue is busy. Please retry later.");
        }

        int maxActiveJobsPerSession = analysisProperties.getMaxActiveJobsPerSession();
        if (maxActiveJobsPerSession > 0
                && sessionId != null
                && !sessionId.isBlank()
                && analyzeJobStore.countActiveJobsForSession(sessionId) >= maxActiveJobsPerSession) {
            throw new ServiceBusyException("This draft session already has too many active analyze jobs.");
        }
    }
}
