package com.filesever.ssh;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import com.filesever.storage.FileInfo;
import com.filesever.storage.FileStorage;
import com.sshtools.common.files.AbstractFile;
import com.sshtools.common.files.AbstractFileFactory;
import com.sshtools.common.files.AbstractFileRandomAccess;
import com.sshtools.common.permissions.PermissionDeniedException;
import com.sshtools.common.sftp.PosixPermissions.PosixPermissionsBuilder;
import com.sshtools.common.sftp.SftpFileAttributes;

public class StorageBackedFile implements AbstractFile {

    private final FileStorage storage;
    private final String path;
    private final String name;
    private final AbstractFileFactory<StorageBackedFile> fileFactory;

    public StorageBackedFile(FileStorage storage, String path,
                             AbstractFileFactory<StorageBackedFile> fileFactory) {
        this.storage = storage;
        this.path = storage.normalize(path);
        this.name = this.path.equals("/") ? "/" :
                this.path.substring(this.path.lastIndexOf('/') + 1);
        this.fileFactory = fileFactory;
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
    public String getCanonicalPath() {
        return path;
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
        return name.startsWith(".");
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    @Override
    public boolean isWritable() {
        return true;
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
    public OutputStream getOutputStream(boolean append) throws IOException {
        return new StorageOutputStream(storage, path);
    }

    @Override
    public List<AbstractFile> getChildren() {
        List<FileInfo> infos = storage.listFiles(path);
        List<AbstractFile> files = new ArrayList<>();
        for (FileInfo info : infos) {
            files.add(new StorageBackedFile(storage, info.path(), fileFactory));
        }
        return files;
    }

    @Override
    public boolean createFolder() {
        storage.createDirectory(path);
        return true;
    }

    @Override
    public boolean delete(boolean recursive) {
        storage.deleteFile(path);
        return true;
    }

    @Override
    public boolean createNewFile() {
        if (storage.exists(path)) return false;
        storage.writeFile(path, new ByteArrayInputStream(new byte[0]), 0);
        return true;
    }

    @Override
    public void truncate() {
        storage.writeFile(path, new ByteArrayInputStream(new byte[0]), 0);
    }

    @Override
    public AbstractFile getParentFile() {
        if (path.equals("/")) return null;
        String parent = path.substring(0, path.lastIndexOf('/'));
        if (parent.isEmpty()) parent = "/";
        return new StorageBackedFile(storage, parent, fileFactory);
    }

    @Override
    public AbstractFile resolveFile(String childPath) {
        String resolved;
        if (childPath.startsWith("/")) {
            resolved = childPath;
        } else if (path.endsWith("/")) {
            resolved = path + childPath;
        } else {
            resolved = path + "/" + childPath;
        }
        return new StorageBackedFile(storage, resolved, fileFactory);
    }

    @Override
    public boolean supportsRandomAccess() {
        return false;
    }

    @Override
    public AbstractFileRandomAccess openFile(boolean writeAccess) {
        throw new UnsupportedOperationException("Random access not supported");
    }

    @Override
    public AbstractFileFactory<? extends AbstractFile> getFileFactory() {
        return fileFactory;
    }

    @Override
    public SftpFileAttributes getAttributes() throws FileNotFoundException {
        FileInfo info = storage.getFileInfo(path);
        if (info == null) throw new FileNotFoundException("File not found: " + path);

        int type = info.directory()
                ? SftpFileAttributes.SSH_FILEXFER_TYPE_DIRECTORY
                : SftpFileAttributes.SSH_FILEXFER_TYPE_REGULAR;

        String permStr = info.directory() ? "rwxr-xr-x" : "rw-r--r--";

        return SftpFileAttributes.SftpFileAttributesBuilder.create()
                .withType(type)
                .withSize(info.size())
                .withLastModifiedTime(FileTime.fromMillis(info.lastModified()))
                .withPermissions(PosixPermissionsBuilder.create()
                        .fromLaxFileModeString(permStr).build())
                .withLastAccessTime(FileTime.fromMillis(info.lastModified()))
                .withGid(0)
                .withUid(0)
                .build();
    }

    @Override
    public void setAttributes(SftpFileAttributes attrs) {
    }

    @Override
    public void refresh() {
    }

    @Override
    public void copyFrom(AbstractFile src) throws IOException {
        if (src.isDirectory()) {
            createFolder();
            for (var f : src.getChildren()) {
                resolveFile(f.getName()).copyFrom(f);
            }
        } else if (src.isFile()) {
            try (var in = src.getInputStream()) {
                try (var out = getOutputStream()) {
                    in.transferTo(out);
                }
            }
        }
    }

    @Override
    public void moveTo(AbstractFile target) throws IOException {
        if (target instanceof StorageBackedFile dest) {
            storage.moveFile(path, dest.path);
        } else {
            copyFrom(target);
            delete(false);
        }
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
            storage.writeFile(path, new ByteArrayInputStream(buf.toByteArray()), buf.size());
        }
    }
}
