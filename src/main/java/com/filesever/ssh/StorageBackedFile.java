package com.filesever.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.filesever.storage.FileInfo;
import com.filesever.storage.FileStorage;
import com.sshtools.common.files.AbstractFile;
import com.sshtools.common.permissions.PermissionDeniedException;

public class StorageBackedFile extends AbstractFile<StorageBackedFile> {

    private final FileStorage storage;
    private final String path;
    private final String name;

    public StorageBackedFile(FileStorage storage, String path) {
        this.storage = storage;
        this.path = storage.normalize(path);
        this.name = this.path.equals("/") ? "/" :
                this.path.substring(this.path.lastIndexOf('/') + 1);
    }

    @Override
    public boolean exists() {
        return storage.exists(path);
    }

    @Override
    public boolean isDirectory() {
        return storage.isDirectory(path);
    }

    @Override
    public boolean isFile() {
        return storage.isFile(path);
    }

    @Override
    public boolean isHidden() {
        return getName().startsWith(".");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAbsolutePath() {
        return path;
    }

    @Override
    public long length() {
        FileInfo info = storage.getFileInfo(path);
        return info != null ? info.size() : 0;
    }

    @Override
    public long lastModified() {
        FileInfo info = storage.getFileInfo(path);
        return info != null ? info.lastModified() : 0;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream in = storage.readFile(path);
        if (in == null) throw new IOException("File not found: " + path);
        return in;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new StorageOutputStream(storage, path);
    }

    @Override
    public List<StorageBackedFile> listFiles() throws IOException {
        List<FileInfo> infos = storage.listFiles(path);
        List<StorageBackedFile> files = new ArrayList<>();
        for (FileInfo info : infos) {
            files.add(new StorageBackedFile(storage, info.path()));
        }
        return files;
    }

    @Override
    public boolean delete() throws IOException {
        storage.deleteFile(path);
        return true;
    }

    @Override
    public boolean mkdir() throws IOException {
        storage.createDirectory(path);
        return true;
    }

    @Override
    public boolean renameTo(StorageBackedFile dest) throws IOException {
        storage.moveFile(path, dest.path);
        return true;
    }

    @Override
    public StorageBackedFile getParent() {
        if (path.equals("/")) return null;
        String parent = path.substring(0, path.lastIndexOf('/'));
        if (parent.isEmpty()) parent = "/";
        return new StorageBackedFile(storage, parent);
    }

    @Override
    public StorageBackedFile resolveFile(String childPath) {
        String resolved;
        if (childPath.startsWith("/")) {
            resolved = childPath;
        } else if (path.endsWith("/")) {
            resolved = path + childPath;
        } else {
            resolved = path + "/" + childPath;
        }
        return new StorageBackedFile(storage, resolved);
    }

    private static class StorageOutputStream extends OutputStream {
        private final FileStorage storage;
        private final String path;
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();

        StorageOutputStream(FileStorage storage, String path) {
            this.storage = storage;
            this.path = path;
        }

        @Override
        public void write(int b) {
            buf.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buf.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            super.close();
            storage.writeFile(path, new java.io.ByteArrayInputStream(buf.toByteArray()), buf.size());
        }
    }
}
