package com.filesever.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.filesever.config.FileServerProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    @Primary
    @ConditionalOnProperty(name = "filesever.storage.type", havingValue = "local", matchIfMissing = true)
    public FileStorage localFileStorage(FileServerProperties props) {
        log.info("Using LOCAL file storage");
        return new LocalFileStorage(props.getFile().getStorageDir());
    }

    @Bean
    @ConditionalOnProperty(name = "filesever.storage.type", havingValue = "s3")
    public FileStorage s3FileStorage(FileServerProperties props) {
        var s3Props = props.getStorage().getS3();
        log.info("Using AWS S3 file storage: bucket={}, region={}", s3Props.getBucket(), s3Props.getRegion());

        S3Client client = S3Client.builder()
                .region(Region.of(s3Props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Props.getAccessKey(), s3Props.getSecretKey())))
                .build();

        return new S3FileStorage(client, s3Props.getBucket());
    }

    @Bean
    @ConditionalOnProperty(name = "filesever.storage.type", havingValue = "obs")
    public FileStorage obsFileStorage(FileServerProperties props) {
        var obsProps = props.getStorage().getObs();
        log.info("Using Huawei OBS file storage: bucket={}, endpoint={}", obsProps.getBucket(), obsProps.getEndpoint());

        com.obs.services.ObsClient client = new com.obs.services.ObsClient(
                obsProps.getAccessKey(), obsProps.getSecretKey(), obsProps.getEndpoint());

        return new ObsFileStorage(client, obsProps.getBucket());
    }
}
