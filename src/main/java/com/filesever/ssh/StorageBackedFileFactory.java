package com.filesever.ssh;

import java.io.IOException;

import com.filesever.storage.FileStorage;
import com.sshtools.common.files.AbstractFileFactory;
import com.sshtools.common.permissions.PermissionDeniedException;

public class StorageBackedFileFactory implements AbstractFileFactory<StorageBackedFile> {

    private final FileStorage storage;

    public StorageBackedFileFactory(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public StorageBackedFile getFile(String path) throws IOException, PermissionDeniedException {
        return new StorageBackedFile(storage, path);
    }
}
