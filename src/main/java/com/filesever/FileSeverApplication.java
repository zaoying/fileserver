package com.filesever;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.filesever.config.FileServerProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileServerProperties.class)
public class FileSeverApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileSeverApplication.class, args);
    }
}
