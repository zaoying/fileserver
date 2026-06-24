package com.filesever.ftps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesever.config.FileServerProperties;
import com.filesever.storage.FileStorage;

public class FtpsServerManager {

    private static final Logger log = LoggerFactory.getLogger(FtpsServerManager.class);

    private final FileServerProperties properties;
    private final FileStorage storage;
    private FtpServer server;

    public FtpsServerManager(FileServerProperties properties, FileStorage storage) {
        this.properties = properties;
        this.storage = storage;
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

        serverFactory.setUserManager(new FtpsUserManager(
                properties.getSsh().getUsers()));

        serverFactory.setFileSystem(new StorageFileSystemFactory(storage));

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
}
