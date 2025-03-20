package com.example.directoryindexer.service;

import java.io.IOException;

public interface IndexingService {
    void indexDirectory(String directoryPath, ProgressCallback progressCallback, LogCallback logCallback) throws IOException;

    interface ProgressCallback {
        void updateProgress(int progress, String status);
    }

    interface LogCallback {
        void log(String level, String message);
    }
} 