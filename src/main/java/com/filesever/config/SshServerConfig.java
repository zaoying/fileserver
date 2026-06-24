package com.filesever.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.filesever.ssh.SshServerManager;
import com.filesever.storage.FileStorage;

import jakarta.annotation.PreDestroy;

@Configuration
public class SshServerConfig {

    private static final Logger log = LoggerFactory.getLogger(SshServerConfig.class);

    private final FileServerProperties properties;
    private final FileStorage storage;
    private SshServerManager sshServerManager;

    public SshServerConfig(FileServerProperties properties, FileStorage storage) {
        this.properties = properties;
        this.storage = storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSshServer() {
        try {
            sshServerManager = new SshServerManager(properties, storage);
            sshServerManager.start();
        } catch (Exception e) {
            log.error("Failed to start SSH server", e);
        }
    }

    @PreDestroy
    public void stopSshServer() {
        if (sshServerManager != null) {
            sshServerManager.stop();
        }
    }
}
