package com.filesever.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.filesever.ssh.SshServerManager;

import jakarta.annotation.PreDestroy;

@Configuration
public class SshServerConfig {

    private static final Logger log = LoggerFactory.getLogger(SshServerConfig.class);

    private final FileServerProperties properties;
    private SshServerManager sshServerManager;

    public SshServerConfig(FileServerProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSshServer() {
        try {
            sshServerManager = new SshServerManager(properties);
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
