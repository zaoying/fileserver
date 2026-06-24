package com.filesever.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.filesever.storage.FileInfo;
import com.filesever.storage.FileStorage;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorage storage;
    private final Map<String, UploadSession> uploads = new ConcurrentHashMap<>();

    public FileController(FileStorage storage) {
        this.storage = storage;
    }

    @GetMapping
    public List<Map<String, Object>> listFiles(@RequestParam(defaultValue = "/") String dir) {
        String path = storage.normalize(dir);
        return storage.listFiles(path).stream()
                .map(f -> Map.<String, Object>of(
                        "name", f.name(),
                        "path", f.path(),
                        "directory", f.directory(),
                        "size", f.size(),
                        "lastModified", f.lastModified()))
                .toList();
    }

    @GetMapping("/download/**")
    public ResponseEntity<Resource> download(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI()
                .substring(request.getContextPath().length() + "/api/files/download/".length());
        path = storage.normalize(path);

        FileInfo info = storage.getFileInfo(path);
        if (info == null || info.directory()) {
            return ResponseEntity.notFound().build();
        }

        InputStream in = storage.readFile(path);
        if (in == null) return ResponseEntity.notFound().build();

        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        String contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(info.size())
                .body(new InputStreamResource(in));
    }

    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "/") String dir) throws IOException {
        String path = storage.normalize(dir + "/" + file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            storage.writeFile(path, in, file.getSize());
        }
        return Map.of("status", "uploaded", "path", path);
    }

    @PostMapping("/upload/init")
    public ResponseEntity<Map<String, Object>> initUpload(@RequestBody Map<String, Object> body) {
        String filename = (String) body.get("filename");
        String dir = (String) body.getOrDefault("dir", "/");
        long totalSize = body.get("totalSize") instanceof Number
                ? ((Number) body.get("totalSize")).longValue() : -1;

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path tempDir = Path.of(".uploads");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }

        String targetPath = storage.normalize(dir + "/" + filename);
        UploadSession session = new UploadSession(uploadId, filename, targetPath,
                totalSize, 0);
        uploads.put(uploadId, session);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/files/upload/" + uploadId)
                .body(Map.of("uploadId", uploadId, "offset", 0, "totalSize", totalSize));
    }

    @PatchMapping("/upload/{uploadId}")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @PathVariable String uploadId,
            @RequestHeader("Upload-Offset") long offset,
            @RequestBody byte[] data) throws IOException {

        UploadSession session = uploads.get(uploadId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        synchronized (session) {
            if (session.currentOffset != offset) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Offset mismatch", "expected", session.currentOffset));
            }
            session.buffer.write(data);
            session.currentOffset += data.length;
            session.totalReceived += data.length;

            if (session.totalSize > 0 && session.totalReceived >= session.totalSize) {
                try (InputStream in = new ByteArrayInputStream(session.buffer.toByteArray())) {
                    storage.writeFile(session.targetPath, in, session.buffer.size());
                }
                uploads.remove(uploadId);
                return ResponseEntity.ok(Map.of(
                        "uploadId", uploadId, "offset", session.currentOffset, "complete", true));
            }
        }

        return ResponseEntity.ok(Map.of("uploadId", uploadId, "offset", session.currentOffset));
    }

    @GetMapping("/upload/{uploadId}")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@PathVariable String uploadId) {
        UploadSession session = uploads.get(uploadId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Upload-Offset", String.valueOf(session.currentOffset))
                .header("Upload-Length", String.valueOf(session.totalSize))
                .body(Map.of("uploadId", uploadId, "offset", session.currentOffset,
                        "totalSize", session.totalSize, "filename", session.filename));
    }

    @DeleteMapping("/upload/{uploadId}")
    public ResponseEntity<Void> cancelUpload(@PathVariable String uploadId) {
        UploadSession session = uploads.remove(uploadId);
        if (session == null) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public Map<String, String> delete(@RequestParam String path) {
        String clean = storage.normalize(path);
        storage.deleteFile(clean);
        return Map.of("status", "deleted", "path", clean);
    }

    @PostMapping("/mkdir")
    public Map<String, String> createDirectory(@RequestParam String path) {
        String clean = storage.normalize(path);
        storage.createDirectory(clean);
        return Map.of("status", "created", "path", clean);
    }

    private static class UploadSession {
        final String uploadId;
        final String filename;
        final String targetPath;
        final long totalSize;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        volatile long currentOffset;
        volatile long totalReceived;

        UploadSession(String uploadId, String filename, String targetPath,
                      long totalSize, long currentOffset) {
            this.uploadId = uploadId;
            this.filename = filename;
            this.targetPath = targetPath;
            this.totalSize = totalSize;
            this.currentOffset = currentOffset;
        }
    }
}
