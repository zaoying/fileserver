package com.filesever.ftps;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;

import com.filesever.storage.FileStorage;

public class StorageFileSystemView implements FileSystemView {

    private final FileStorage storage;
    private final User user;
    private final String homeDir;
    private String workingDir;

    public StorageFileSystemView(FileStorage storage, User user) {
        this.storage = storage;
        this.user = user;
        this.homeDir = "/" + user.getName();
        this.workingDir = this.homeDir;
        storage.createDirectory(homeDir);
    }

    @Override
    public FtpFile getHomeDirectory() {
        return new StorageBackedFtpFile(storage, homeDir, user);
    }

    @Override
    public FtpFile getWorkingDirectory() {
        return new StorageBackedFtpFile(storage, workingDir, user);
    }

    @Override
    public boolean changeWorkingDirectory(String dir) {
        String newDir;
        if (dir.startsWith("/")) {
            newDir = dir;
        } else if (workingDir.equals("/")) {
            newDir = "/" + dir;
        } else {
            newDir = workingDir + "/" + dir;
        }
        newDir = storage.normalize(newDir);
        if (storage.exists(newDir) && storage.isDirectory(newDir)) {
            workingDir = newDir;
            return true;
        }
        return false;
    }

    @Override
    public FtpFile getFile(String file) {
        String resolved;
        if (file.startsWith("/")) {
            resolved = file;
        } else if (workingDir.equals("/")) {
            resolved = "/" + file;
        } else {
            resolved = workingDir + "/" + file;
        }
        return new StorageBackedFtpFile(storage, resolved, user);
    }

    @Override
    public boolean isRandomAccessible() {
        return false;
    }

    @Override
    public void dispose() {
    }
}
