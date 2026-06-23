package com.filesever.controller;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
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

import com.filesever.config.FileServerProperties;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final Path storageDir;
    private final Map<String, UploadSession> uploads = new ConcurrentHashMap<>();

    public FileController(FileServerProperties properties) {
        this.storageDir = properties.getFile().getStorageDir().toAbsolutePath();
    }

    private Path safeResolve(String path) {
        Path target = storageDir.resolve(path).normalize();
        if (!target.startsWith(storageDir)) {
            return null;
        }
        return target;
    }

    @GetMapping
    public List<String> listFiles(@RequestParam(defaultValue = "/") String dir) throws IOException {
        Path target = safeResolve(dir);
        if (target == null) return List.of("Access denied");
        try (Stream<Path> paths = Files.list(target)) {
            return paths.map(p -> Files.isDirectory(p)
                    ? p.getFileName().toString() + "/"
                    : p.getFileName().toString()).toList();
        }
    }

    @GetMapping("/download/**")
    public ResponseEntity<Resource> download(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI()
                .substring(request.getContextPath() + "/api/files/download/".length());
        Path target = safeResolve(path);
        if (target == null || !Files.exists(target) || Files.isDirectory(target)) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(target.toFile());
        String contentType = Files.probeContentType(target);
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + target.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(Files.size(target))
                .body(resource);
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(defaultValue = "/") String dir) throws IOException {
        Path targetDir = safeResolve(dir);
        if (targetDir == null) return "Access denied";
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(file.getOriginalFilename());
        file.transferTo(target.toFile());
        return "Uploaded: " + target.getFileName();
    }

    @PostMapping("/upload/init")
    public ResponseEntity<Map<String, Object>> initUpload(@RequestBody Map<String, Object> body) {
        String filename = (String) body.get("filename");
        String dir = (String) body.getOrDefault("dir", "/");
        long totalSize = body.get("totalSize") instanceof Number
                ? ((Number) body.get("totalSize")).longValue() : -1;

        Path targetDir = safeResolve(dir);
        if (targetDir == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Access denied"));
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path tempFile = storageDir.resolve(".uploads").resolve(uploadId + ".tmp");
        try {
            Files.createDirectories(tempFile.getParent());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }

        UploadSession session = new UploadSession(uploadId, filename, targetDir.resolve(filename),
                tempFile, totalSize, 0);
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
            try (RandomAccessFile raf = new RandomAccessFile(session.tempFile.toFile(), "rw")) {
                raf.seek(offset);
                raf.write(data);
            }
            session.currentOffset += data.length;

            if (session.totalSize > 0 && session.currentOffset >= session.totalSize) {
                Files.createDirectories(session.targetPath.getParent());
                Files.move(session.tempFile, session.targetPath, StandardCopyOption.ATOMIC_MOVE);
                uploads.remove(uploadId);
                return ResponseEntity.ok(Map.of("uploadId", uploadId, "offset", session.currentOffset, "complete", true));
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
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Files.deleteIfExists(session.tempFile);
        } catch (IOException ignored) {}
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public String delete(@RequestParam String path) throws IOException {
        Path target = safeResolve(path);
        if (target == null) return "Access denied";
        Files.deleteIfExists(target);
        return "Deleted: " + target.getFileName();
    }

    @PostMapping("/mkdir")
    public String createDirectory(@RequestParam String path) throws IOException {
        Path target = safeResolve(path);
        if (target == null) return "Access denied";
        Files.createDirectories(target);
        return "Created: " + target;
    }

    private static class UploadSession {
        final String uploadId;
        final String filename;
        final Path targetPath;
        final Path tempFile;
        final long totalSize;
        volatile long currentOffset;

        UploadSession(String uploadId, String filename, Path targetPath,
                      Path tempFile, long totalSize, long currentOffset) {
            this.uploadId = uploadId;
            this.filename = filename;
            this.targetPath = targetPath;
            this.tempFile = tempFile;
            this.totalSize = totalSize;
            this.currentOffset = currentOffset;
        }
    }
}
