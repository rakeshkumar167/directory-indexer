package com.example.directoryindexer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class LuceneIndexService implements IndexingService {
    private final Directory indexDirectory;

    public LuceneIndexService(@Value("${lucene.index.dir}") String indexDir) throws IOException {
        // Resolve the index directory path relative to the project root
        Path indexPath = Paths.get(indexDir).toAbsolutePath();
        
        // Create directory if it doesn't exist
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);
            log.info("Created index directory at: {}", indexPath);
        }
        
        // Clean up any existing index files if they're corrupted
        try {
            if (Files.list(indexPath).count() == 0 || 
                (Files.list(indexPath).count() == 1 && Files.list(indexPath).anyMatch(p -> p.getFileName().toString().equals(".DS_Store")))) {
                Files.list(indexPath).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete file: {}", path, e);
                    }
                });
                log.info("Cleaned up index directory at: {}", indexPath);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up index directory: {}", e.getMessage());
        }
        
        this.indexDirectory = FSDirectory.open(indexPath);
        log.info("Initialized Lucene index directory at: {}", indexPath);
    }

    @Override
    public void indexDirectory(String directoryPath, ProgressCallback progressCallback, LogCallback logCallback) throws IOException {
        Path dirPath = Paths.get(directoryPath);
        if (!Files.exists(dirPath)) {
            throw new IllegalArgumentException("Directory does not exist: " + directoryPath);
        }
        if (!Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Path is not a directory: " + directoryPath);
        }
        if (!Files.isReadable(dirPath)) {
            throw new IllegalArgumentException("Directory is not readable: " + directoryPath);
        }

        // Count total files for progress calculation
        AtomicInteger totalFiles = new AtomicInteger(0);
        try {
            Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .forEach(path -> totalFiles.incrementAndGet());
        } catch (IOException e) {
            log.error("Error walking directory: {}", dirPath, e);
            throw new IOException("Failed to scan directory: " + e.getMessage(), e);
        }

        if (totalFiles.get() == 0) {
            logCallback.log("WARNING", "No files found in the specified directory");
            return;
        }

        logCallback.log("INFO", "Found " + totalFiles.get() + " files to index");

        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // This will overwrite existing index
        
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            AtomicInteger processedFiles = new AtomicInteger(0);
            try {
                Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            indexFile(path, writer, logCallback);
                            int progress = (int) ((processedFiles.incrementAndGet() * 100.0) / totalFiles.get());
                            progressCallback.updateProgress(progress, "Indexing file: " + path.getFileName());
                        } catch (IOException e) {
                            log.error("Error indexing file: {}", path, e);
                            logCallback.log("ERROR", "Failed to index file: " + path + " - " + e.getMessage());
                        }
                    });
            } catch (IOException e) {
                log.error("Error walking directory during indexing: {}", dirPath, e);
                throw new IOException("Failed to index directory: " + e.getMessage(), e);
            }

            writer.commit();
            logCallback.log("INFO", "Indexing completed successfully");
            log.info("Index created successfully at: {}", indexDirectory);
        } catch (IOException e) {
            log.error("Error with index writer: {}", e.getMessage(), e);
            throw new IOException("Failed to create or use index writer: " + e.getMessage(), e);
        } finally {
            analyzer.close();
        }
    }

    private void indexFile(Path filePath, IndexWriter writer, LogCallback logCallback) throws IOException {
        try {
            Document doc = new Document();
            String path = filePath.toString();
            String filename = filePath.getFileName().toString().toLowerCase();
            doc.add(new StringField("path", path, Field.Store.YES));
            doc.add(new TextField("filename", filename, Field.Store.YES));
            writer.addDocument(doc);
            log.debug("Indexed file: {} (path: {})", filename, path);
            logCallback.log("INFO", "Indexed file: " + filePath.getFileName());
        } catch (IOException e) {
            log.error("Error indexing file: {}", filePath, e);
            throw new IOException("Failed to index file: " + filePath + " - " + e.getMessage(), e);
        }
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