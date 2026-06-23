package com.filesever.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesever.config.FileServerProperties;
import com.filesever.config.FileServerProperties.User;
import com.sshtools.common.files.vfs.VFSFileFactory;
import com.sshtools.common.ssh.SshException;
import com.sshtools.server.SshServer;
import com.sshtools.server.authentication.InMemoryPasswordAuthenticator;
import com.sshtools.server.virtual.VirtualFileFactory;
import com.sshtools.server.virtual.VirtualMountTemplate;

public class SshServerManager {

    private static final Logger log = LoggerFactory.getLogger(SshServerManager.class);

    private final FileServerProperties properties;
    private final Path storageDir;
    private SshServer server;

    public SshServerManager(FileServerProperties properties) {
        this.properties = properties;
        this.storageDir = properties.getFile().getStorageDir().toAbsolutePath();
    }

    public void start() throws IOException, SshException {
        Path hostKeyDir = properties.getSsh().getHostKeyDir().toAbsolutePath();
        Files.createDirectories(storageDir);
        Files.createDirectories(hostKeyDir);

        server = new SshServer(properties.getSsh().getPort());
        server.addAuthenticator(createAuthenticator(properties.getSsh().getUsers()));
        configureFileSystem(server, storageDir);

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

    private void configureFileSystem(SshServer server, Path storageDir) {
        server.setFileFactory(con -> {
            String userHome = storageDir.resolve(con.getUsername()).toString();
            try {
                Files.createDirectories(Path.of(userHome));
            } catch (IOException e) {
                throw new RuntimeException("Failed to create user directory: " + userHome, e);
            }
            return new VirtualFileFactory(
                new VirtualMountTemplate("/", userHome, new VFSFileFactory(), true)
            );
        });
    }
}
