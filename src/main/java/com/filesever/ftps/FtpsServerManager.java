package com.filesever.ftps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesever.config.FileServerProperties;
import com.filesever.config.FileServerProperties.User;

public class FtpsServerManager {

    private static final Logger log = LoggerFactory.getLogger(FtpsServerManager.class);

    private final FileServerProperties properties;
    private final Path storageDir;
    private FtpServer server;

    public FtpsServerManager(FileServerProperties properties) {
        this.properties = properties;
        this.storageDir = properties.getFile().getStorageDir().toAbsolutePath();
    }

    public void start() throws FtpException, IOException, InterruptedException {
        Path keystoreFile = properties.getFtps().getKeystoreFile().toAbsolutePath();
        String keystorePassword = properties.getFtps().getKeystorePassword();

        if (!Files.exists(keystoreFile)) {
            generateKeystore(keystoreFile, keystorePassword);
        }

        FtpServerFactory serverFactory = new FtpServerFactory();

        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(properties.getFtps().getPort());

        SslConfigurationFactory ssl = new SslConfigurationFactory();
        ssl.setKeystoreFile(keystoreFile.toFile());
        ssl.setKeystorePassword(keystorePassword);
        ssl.setKeyPassword(keystorePassword);
        ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
        listenerFactory.setSslConfiguration(ssl.createSslConfiguration());
        listenerFactory.setImplicitSsl(properties.getFtps().isImplicitSsl());

        serverFactory.addListener("default", listenerFactory.createListener());

        serverFactory.setUserManager(new InMemoryFtpsUserManager(
                properties.getSsh().getUsers(), storageDir));

        org.apache.ftpserver.filesystem.nativeimpl.NativeFileSystemFactory fsFactory =
                new org.apache.ftpserver.filesystem.nativeimpl.NativeFileSystemFactory();
        fsFactory.setCreateHome(true);
        serverFactory.setFileSystem(fsFactory);

        server = serverFactory.createServer();
        server.start();
        log.info("FTPS server started on port {}", properties.getFtps().getPort());
    }

    public void stop() {
        if (server != null) {
            server.stop();
            log.info("FTPS server stopped");
        }
    }

    private void generateKeystore(Path keystoreFile, String password)
            throws IOException, InterruptedException {
        Files.createDirectories(keystoreFile.getParent());
        List<String> cmd = List.of(
                "keytool", "-genkey", "-alias", "ftp", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "3650",
                "-keystore", keystoreFile.toAbsolutePath().toString(),
                "-storepass", password, "-keypass", password,
                "-dname", "CN=FileSever, OU=FileSever, O=FileSever, L=Unknown, ST=Unknown, C=US",
                "-noprompt"
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        int exitCode = pb.start().waitFor();
        if (exitCode != 0) {
            throw new IOException("Failed to generate keystore, exit code: " + exitCode);
        }
        log.info("Generated FTPS keystore at {}", keystoreFile);
    }

    private static class InMemoryFtpsUserManager implements UserManager {

        private final Map<String, User> users = new HashMap<>();

        InMemoryFtpsUserManager(List<User> userList, Path storageDir) {
            for (User u : userList) {
                BaseUser user = new BaseUser();
                user.setName(u.getUsername());
                user.setPassword(u.getPassword());
                user.setHomeDirectory(storageDir.resolve(u.getUsername()).toString());
                user.setEnabled(true);
                List<Authority> authorities = new ArrayList<>();
                authorities.add(new WritePermission());
                user.setAuthorities(authorities);
                users.put(u.getUsername(), user);
            }
        }

        @Override
        public User authenticate(Authentication authentication)
                throws AuthenticationFailedException {
            if (authentication instanceof org.apache.ftpserver.ftplet.UsernamePasswordAuthentication) {
                var auth = (org.apache.ftpserver.ftplet.UsernamePasswordAuthentication) authentication;
                User user = users.get(auth.getUsername());
                if (user != null && user.getPassword().equals(auth.getPassword())) {
                    return user;
                }
            }
            throw new AuthenticationFailedException("Authentication failed");
        }

        @Override
        public User getUserByName(String name) { return users.get(name); }

        @Override
        public String[] getAllUserNames() {
            return users.keySet().toArray(new String[0]);
        }

        @Override
        public void save(User user) {
            users.put(user.getName(), user);
        }

        @Override
        public boolean doesExist(String name) {
            return users.containsKey(name);
        }

        @Override
        public User delete(String name) {
            return users.remove(name);
        }

        @Override
        public boolean isAdmin(String name) {
            return false;
        }

        @Override
        public String getAdminName() {
            return null;
        }

        @Override
        public boolean isPasswordCorrect(String name, String password) {
            User user = users.get(name);
            return user != null && user.getPassword().equals(password);
        }
    }
}
