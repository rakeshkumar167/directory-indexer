package com.example.directoryindexer.service;

import com.example.directoryindexer.model.SearchResult;
import java.util.List;

public interface SearchService {
    List<SearchResult> search(String query);
} 