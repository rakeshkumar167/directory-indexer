package com.example.directoryindexer.controller;

import com.example.directoryindexer.service.LuceneSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final LuceneSearchService searchService;

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, Model model) {
        if (q != null && !q.trim().isEmpty()) {
            try {
                model.addAttribute("query", q);
                List<LuceneSearchService.FileInfo> results = searchService.searchFiles(q);
                model.addAttribute("results", results);
            } catch (IOException e) {
                model.addAttribute("error", "Error performing search: " + e.getMessage());
            }
        }
        return "index";
    }
} 