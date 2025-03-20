package com.example.directoryindexer.controller;

import com.example.directoryindexer.service.IndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private final IndexingService indexingService;
    private final AtomicReference<IndexingProgress> currentProgress = new AtomicReference<>();
    private final List<LogEntry> logEntries = new ArrayList<>();

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/index")
    public String showIndexForm() {
        return "index-directory";
    }

    @PostMapping("/index")
    @ResponseBody
    public String indexDirectory(@RequestParam String directoryPath, RedirectAttributes redirectAttributes) {
        try {
            // Reset progress and logs
            currentProgress.set(new IndexingProgress(0, "Starting indexing process...", false, true, 0));
            logEntries.clear();
            logEntries.add(new LogEntry("INFO", "Starting indexing process for directory: " + directoryPath, java.time.LocalDateTime.now().toString()));

            // Start indexing in a separate thread
            new Thread(() -> {
                try {
                    indexingService.indexDirectory(directoryPath, this::updateProgress, this::addLog);
                    currentProgress.set(new IndexingProgress(100, "Indexing completed successfully!", true, true, logEntries.size()));
                    logEntries.add(new LogEntry("INFO", "Indexing completed successfully! Total documents indexed: " + logEntries.size(), java.time.LocalDateTime.now().toString()));
                } catch (Exception e) {
                    currentProgress.set(new IndexingProgress(0, "Error during indexing: " + e.getMessage(), true, false, 0));
                    logEntries.add(new LogEntry("ERROR", "Error during indexing: " + e.getMessage(), java.time.LocalDateTime.now().toString()));
                }
            }).start();

            return "Indexing started successfully";
        } catch (Exception e) {
            throw new RuntimeException("Failed to start indexing: " + e.getMessage());
        }
    }

    @GetMapping("/index/progress")
    @ResponseBody
    public IndexingProgress getProgress() {
        IndexingProgress progress = currentProgress.get();
        if (progress != null) {
            progress.setLogs(new ArrayList<>(logEntries));
        }
        return progress;
    }

    private void updateProgress(int progress, String status) {
        currentProgress.set(new IndexingProgress(progress, status, false, true, logEntries.size()));
    }

    private void addLog(String level, String message) {
        logEntries.add(new LogEntry(level, message, java.time.LocalDateTime.now().toString()));
    }

    public static class IndexingProgress {
        private int progress;
        private String status;
        private boolean completed;
        private boolean success;
        private String error;
        private List<LogEntry> logs;
        private int documentCount;

        public IndexingProgress() {}

        public IndexingProgress(int progress, String status, boolean completed, boolean success, int documentCount) {
            this.progress = progress;
            this.status = status;
            this.completed = completed;
            this.success = success;
            this.documentCount = documentCount;
            this.logs = new ArrayList<>();
        }

        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public List<LogEntry> getLogs() { return logs; }
        public void setLogs(List<LogEntry> logs) { this.logs = logs; }
        public int getDocumentCount() { return documentCount; }
        public void setDocumentCount(int documentCount) { this.documentCount = documentCount; }
    }

    public static class LogEntry {
        private String level;
        private String message;
        private String timestamp;

        public LogEntry() {}

        public LogEntry(String level, String message, String timestamp) {
            this.level = level;
            this.message = message;
            this.timestamp = timestamp;
        }

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
} 