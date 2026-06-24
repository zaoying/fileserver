package com.filesever.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.filesever.ftps.FtpsServerManager;
import com.filesever.storage.FileStorage;

import jakarta.annotation.PreDestroy;

@Configuration
public class FtpsServerConfig {

    private static final Logger log = LoggerFactory.getLogger(FtpsServerConfig.class);

    private final FileServerProperties properties;
    private final FileStorage storage;
    private FtpsServerManager ftpsServerManager;

    public FtpsServerConfig(FileServerProperties properties, FileStorage storage) {
        this.properties = properties;
        this.storage = storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startFtpsServer() {
        try {
            ftpsServerManager = new FtpsServerManager(properties, storage);
            ftpsServerManager.start();
        } catch (Exception e) {
            log.error("Failed to start FTPS server", e);
        }
    }

    @PreDestroy
    public void stopFtpsServer() {
        if (ftpsServerManager != null) {
            ftpsServerManager.stop();
        }
    }
}
