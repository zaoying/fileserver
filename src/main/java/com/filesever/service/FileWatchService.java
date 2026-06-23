package com.filesever.service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class FileWatchService {

    private static final Logger log = LoggerFactory.getLogger(FileWatchService.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    public void startWatching(Path dir) {
        running = true;
        Thread watcher = new Thread(() -> {
            try (WatchService watchService = dir.getFileSystem().newWatchService()) {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                log.info("File watch service started on {}", dir);

                while (running) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path filename = (Path) event.context();
                        String kind = event.kind().name();
                        String filePath = dir.resolve(filename).toString();
                        broadcast(Map.of("type", kind, "path", filePath, "file", filename.toString()));
                    }
                    if (!key.reset()) break;
                }
            } catch (IOException | InterruptedException e) {
                if (running) {
                    log.error("File watch service error", e);
                }
            }
        }, "file-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    public void stop() {
        running = false;
    }

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("status", "connected")));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    private void broadcast(Map<String, Object> data) {
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("file-change")
                        .data(data));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
