package com.filesever.ftps;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;

import com.filesever.storage.FileStorage;

public class StorageFileSystemView implements FileSystemView {

    private final FileStorage storage;
    private final User user;
    private final String homePrefix;
    private String workingDir = "/";

    public StorageFileSystemView(FileStorage storage, User user) {
        this.storage = storage;
        this.user = user;
        this.homePrefix = "/" + user.getName();
        storage.createDirectory(homePrefix);
    }

    private String storagePath(String userPath) {
        String norm = storage.normalize(userPath);
        if (norm.equals("/") || norm.equals(homePrefix)) return homePrefix;
        if (norm.startsWith(homePrefix + "/")) return norm;
        return storage.normalize(homePrefix + norm);
    }

    private FtpFile createFile(String userPath) {
        return new StorageBackedFtpFile(storage, storagePath(userPath), userPath, user);
    }

    @Override
    public FtpFile getHomeDirectory() {
        return createFile("/");
    }

    @Override
    public FtpFile getWorkingDirectory() {
        return createFile(workingDir);
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

        String storagePath = storagePath(newDir);
        if (storage.exists(storagePath) && storage.isDirectory(storagePath)) {
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
        return createFile(resolved);
    }

    @Override
    public boolean isRandomAccessible() {
        return false;
    }

    @Override
    public void dispose() {
    }
}
