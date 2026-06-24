package com.filesever.storage;

public record FileInfo(String name, String path, boolean directory, long size, long lastModified) {

    public boolean isFile() {
        return !directory;
    }
}
