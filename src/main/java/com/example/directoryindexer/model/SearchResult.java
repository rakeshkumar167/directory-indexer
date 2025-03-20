package com.example.directoryindexer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String path;
    private String content;
    private String highlightedContent;
} 