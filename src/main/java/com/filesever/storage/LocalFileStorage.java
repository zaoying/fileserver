package com.filesever.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public class LocalFileStorage implements FileStorage {

    private final Path baseDir;

    public LocalFileStorage(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
    }

    private Path resolve(String path) {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        return baseDir.resolve(clean).normalize();
    }

    private String toStoragePath(Path absPath) {
        String rel = baseDir.relativize(absPath).toString().replace("\\", "/");
        return "/" + rel;
    }

    @Override
    public List<FileInfo> listFiles(String path) {
        Path target = resolve(path);
        if (!Files.exists(target) || !Files.isDirectory(target)) return List.of();
        try (Stream<Path> paths = Files.list(target)) {
            return paths.map(p -> {
                try {
                    return new FileInfo(
                            p.getFileName().toString(),
                            toStoragePath(p),
                            Files.isDirectory(p),
                            Files.size(p),
                            p.toFile().lastModified()
                    );
                } catch (IOException e) {
                    return null;
                }
            }).filter(f -> f != null).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public InputStream readFile(String path) {
        try { return Files.newInputStream(resolve(path)); }
        catch (IOException e) { return null; }
    }

    @Override
    public void writeFile(String path, InputStream data, long size) {
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target)) {
                data.transferTo(out);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + path, e);
        }
    }

    @Override
    public void deleteFile(String path) {
        try { Files.deleteIfExists(resolve(path)); }
        catch (IOException ignored) {}
    }

    @Override
    public void createDirectory(String path) {
        try { Files.createDirectories(resolve(path)); }
        catch (IOException e) { throw new RuntimeException("Failed to create directory: " + path, e); }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    @Override
    public FileInfo getFileInfo(String path) {
        Path target = resolve(path);
        if (!Files.exists(target)) return null;
        try {
            return new FileInfo(
                    target.getFileName().toString(),
                    toStoragePath(target),
                    Files.isDirectory(target),
                    Files.size(target),
                    target.toFile().lastModified()
            );
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void copyFile(String source, String dest) {
        try { Files.copy(resolve(source), resolve(dest), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { throw new RuntimeException("Failed to copy: " + source + " -> " + dest, e); }
    }

    @Override
    public void moveFile(String source, String dest) {
        try { Files.move(resolve(source), resolve(dest), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { throw new RuntimeException("Failed to move: " + source + " -> " + dest, e); }
    }

    @Override
    public String normalize(String path) {
        String clean = path.replace("\\", "/");
        if (!clean.startsWith("/")) clean = "/" + clean;
        if (clean.endsWith("/") && clean.length() > 1) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }
}
