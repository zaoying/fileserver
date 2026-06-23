package com.filesever.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.filesever.service.FileWatchService;

@RestController
@RequestMapping("/api/files")
public class FileWatchController {

    private final FileWatchService watchService;

    public FileWatchController(FileWatchService watchService) {
        this.watchService = watchService;
    }

    @GetMapping(value = "/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter watchFiles() {
        return watchService.createEmitter();
    }
}
