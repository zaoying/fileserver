package com.filesever.storage;

import java.io.InputStream;
import java.util.List;

public interface FileStorage {

    List<FileInfo> listFiles(String path);

    InputStream readFile(String path);

    void writeFile(String path, InputStream data, long size);

    void deleteFile(String path);

    void createDirectory(String path);

    boolean exists(String path);

    FileInfo getFileInfo(String path);

    void copyFile(String source, String dest);

    void moveFile(String source, String dest);

    default boolean isDirectory(String path) {
        FileInfo info = getFileInfo(path);
        return info != null && info.directory();
    }

    default boolean isFile(String path) {
        FileInfo info = getFileInfo(path);
        return info != null && info.isFile();
    }

    String normalize(String path);
}
