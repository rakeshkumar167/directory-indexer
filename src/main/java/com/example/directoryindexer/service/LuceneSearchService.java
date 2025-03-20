package com.example.directoryindexer.service;

import com.example.directoryindexer.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LuceneSearchService {
    private final Directory indexDirectory;

    public LuceneSearchService(@Value("${lucene.index.dir}") String indexDir) throws IOException {
        // Resolve the index directory path relative to the project root
        Path indexPath = Paths.get(indexDir).toAbsolutePath();
        
        // Create directory if it doesn't exist
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);
            log.info("Created index directory at: {}", indexPath);
        }
        
        this.indexDirectory = FSDirectory.open(indexPath);
        log.info("Initialized Lucene search directory at: {}", indexPath);
    }

    public List<FileInfo> searchFiles(String query) throws IOException {
        List<FileInfo> results = new ArrayList<>();
        
        // Check if index exists
        if (!DirectoryReader.indexExists(indexDirectory)) {
            log.warn("Index does not exist at: {}. Please index a directory first.", indexDirectory);
            return results;
        }
        
        try (IndexReader reader = DirectoryReader.open(indexDirectory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();
            
            // Convert query to lowercase for case-insensitive search
            String lowercaseQuery = query.toLowerCase().trim();
            log.info("Searching for query: {} in index at: {}", lowercaseQuery, indexDirectory);
            
            // Create a more flexible query that matches partial words
            QueryParser filenameParser = new QueryParser("filename", analyzer);
            Query filenameQuery = filenameParser.parse(lowercaseQuery + "*");
            
            // Search for documents
            TopDocs filenameDocs = searcher.search(filenameQuery, 100);
            log.info("Found {} results", filenameDocs.totalHits.value);
            
            // Process results
            for (ScoreDoc scoreDoc : filenameDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String path = doc.get("path");
                String filename = doc.get("filename");
                log.debug("Found match: {} (score: {})", filename, scoreDoc.score);
                results.add(new FileInfo(
                    path,
                    filename,
                    "File name match"
                ));
            }
        } catch (Exception e) {
            log.error("Error during search in index at: {}: {}", indexDirectory, e.getMessage(), e);
            throw new IOException("Failed to perform search: " + e.getMessage(), e);
        }
        
        return results;
    }

    public static class FileInfo {
        private String path;
        private String filename;
        private String type;

        public FileInfo(String path, String filename, String type) {
            this.path = path;
            this.filename = filename;
            this.type = type;
        }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
} 