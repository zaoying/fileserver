package com.filesever.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.filesever.service.FileWatchService;

@Configuration
public class WatchServiceConfig {

    private final FileServerProperties properties;
    private final FileWatchService watchService;

    public WatchServiceConfig(FileServerProperties properties, FileWatchService watchService) {
        this.properties = properties;
        this.watchService = watchService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWatching() {
        watchService.startWatching(properties.getFile().getStorageDir().toAbsolutePath());
    }
}
