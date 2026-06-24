package com.filesever.ftps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;

import com.filesever.storage.FileInfo;
import com.filesever.storage.FileStorage;

public class StorageBackedFtpFile implements FtpFile {

    private final FileStorage storage;
    private final String path;
    private final String name;
    private final User user;

    public StorageBackedFtpFile(FileStorage storage, String path, User user) {
        this.storage = storage;
        this.path = storage.normalize(path);
        this.name = this.path.equals("/") ? "/" :
                this.path.substring(this.path.lastIndexOf('/') + 1);
        this.user = user;
    }

    @Override
    public String getAbsolutePath() {
        return path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isHidden() {
        return name.startsWith(".");
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
    public boolean doesExist() {
        return storage.exists(path);
    }

    @Override
    public long getSize() {
        FileInfo info = storage.getFileInfo(path);
        return info != null ? info.size() : 0;
    }

    @Override
    public long getLastModified() {
        FileInfo info = storage.getFileInfo(path);
        return info != null ? info.lastModified() : System.currentTimeMillis();
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
    public boolean isRemovable() {
        return true;
    }

    @Override
    public String getOwnerName() {
        return user.getName();
    }

    @Override
    public String getGroupName() {
        return user.getName();
    }

    @Override
    public int getLinkCount() {
        return isDirectory() ? 2 : 1;
    }

    @Override
    public long getLastAccessTime() {
        return getLastModified();
    }

    @Override
    public long getCreationTime() {
        return getLastModified();
    }

    @Override
    public boolean setLastModified(long time) {
        return false;
    }

    @Override
    public boolean mkdir() {
        storage.createDirectory(path);
        return true;
    }

    @Override
    public boolean delete() {
        storage.deleteFile(path);
        return true;
    }

    @Override
    public boolean move(FtpFile destination) {
        if (destination instanceof StorageBackedFtpFile dest) {
            storage.moveFile(path, dest.path);
            return true;
        }
        return false;
    }

    @Override
    public List<? extends FtpFile> listFiles() {
        List<FileInfo> infos = storage.listFiles(path);
        List<StorageBackedFtpFile> files = new ArrayList<>();
        for (FileInfo info : infos) {
            files.add(new StorageBackedFtpFile(storage, info.path(), user));
        }
        return files;
    }

    @Override
    public OutputStream createOutputStream(long offset) {
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                storage.writeFile(path, new ByteArrayInputStream(toByteArray()), size());
            }
        };
    }

    @Override
    public InputStream createInputStream(long offset) {
        InputStream in = storage.readFile(path);
        if (in == null) return null;
        try {
            in.skip(offset);
        } catch (IOException e) {
            return null;
        }
        return in;
    }

    @Override
    public File getPhysicalFile() {
        return null;
    }
}
