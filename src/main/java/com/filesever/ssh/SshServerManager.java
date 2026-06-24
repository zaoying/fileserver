package com.filesever.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesever.config.FileServerProperties;
import com.filesever.config.FileServerProperties.User;
import com.filesever.storage.FileStorage;
import com.sshtools.common.ssh.SshException;
import com.sshtools.server.SshServer;
import com.sshtools.server.authentication.InMemoryPasswordAuthenticator;

public class SshServerManager {

    private static final Logger log = LoggerFactory.getLogger(SshServerManager.class);

    private final FileServerProperties properties;
    private final FileStorage storage;
    private SshServer server;

    public SshServerManager(FileServerProperties properties, FileStorage storage) {
        this.properties = properties;
        this.storage = storage;
    }

    public void start() throws IOException, SshException {
        Path hostKeyDir = properties.getSsh().getHostKeyDir().toAbsolutePath();
        Files.createDirectories(hostKeyDir);

        server = new SshServer(properties.getSsh().getPort());
        server.addAuthenticator(createAuthenticator(properties.getSsh().getUsers()));
        configureFileSystem(server);

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        server.start();
        log.info("SSH server started on port {}", properties.getSsh().getPort());
    }

    public void stop() {
        if (server != null) {
            try {
                server.close();
                log.info("SSH server stopped");
            } catch (Exception e) {
                log.error("Error stopping SSH server", e);
            }
        }
    }

    private InMemoryPasswordAuthenticator createAuthenticator(List<User> users) {
        InMemoryPasswordAuthenticator auth = new InMemoryPasswordAuthenticator();
        for (User user : users) {
            auth.addUser(user.getUsername(), user.getPassword().toCharArray());
            log.info("Added user: {}", user.getUsername());
        }
        return auth;
    }

    private void configureFileSystem(SshServer server) {
        server.setFileFactory(con -> {
            String userHome = "/" + con.getUsername();
            storage.createDirectory(userHome);
            return new StorageBackedFileFactory(
                    new UserHomeFileStorage(storage, userHome));
        });
    }

    private record UserHomeFileStorage(FileStorage delegate, String homePrefix)
            implements FileStorage {

        private String userPath(String path) {
            String normalized = delegate.normalize(path);
            if (normalized.equals("/")) return homePrefix;
            if (normalized.startsWith(homePrefix + "/") || normalized.equals(homePrefix)) {
                return normalized;
            }
            return homePrefix + normalized;
        }

        private com.filesever.storage.FileInfo stripPrefix(com.filesever.storage.FileInfo info) {
            String relPath = info.path();
            if (relPath.equals(homePrefix)) return new com.filesever.storage.FileInfo(
                    info.name(), "/", info.directory(), info.size(), info.lastModified());
            if (relPath.startsWith(homePrefix + "/")) {
                relPath = relPath.substring(homePrefix.length());
            }
            return new com.filesever.storage.FileInfo(
                    info.name(), relPath, info.directory(), info.size(), info.lastModified());
        }

        @Override
        public List<com.filesever.storage.FileInfo> listFiles(String path) {
            return delegate.listFiles(userPath(path)).stream()
                    .map(this::stripPrefix).toList();
        }

        @Override
        public java.io.InputStream readFile(String path) {
            return delegate.readFile(userPath(path));
        }

        @Override
        public void writeFile(String path, java.io.InputStream data, long size) {
            delegate.writeFile(userPath(path), data, size);
        }

        @Override
        public void deleteFile(String path) {
            delegate.deleteFile(userPath(path));
        }

        @Override
        public void createDirectory(String path) {
            delegate.createDirectory(userPath(path));
        }

        @Override
        public boolean exists(String path) {
            return delegate.exists(userPath(path));
        }

        @Override
        public com.filesever.storage.FileInfo getFileInfo(String path) {
            var info = delegate.getFileInfo(userPath(path));
            return info != null ? stripPrefix(info) : null;
        }

        @Override
        public void copyFile(String source, String dest) {
            delegate.copyFile(userPath(source), userPath(dest));
        }

        @Override
        public void moveFile(String source, String dest) {
            delegate.moveFile(userPath(source), userPath(dest));
        }

        @Override
        public String normalize(String path) {
            return delegate.normalize(path);
        }
    }
}
