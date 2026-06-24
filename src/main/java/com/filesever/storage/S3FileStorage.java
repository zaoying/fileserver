package com.filesever.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStorage(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    private String toKey(String path) {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        return clean.isEmpty() ? "" : clean;
    }

    private String toPath(String key) {
        return "/" + (key.isEmpty() ? "" : key);
    }

    @Override
    public List<FileInfo> listFiles(String path) {
        String prefix = toKey(path);
        if (!prefix.isEmpty() && !prefix.endsWith("/")) prefix += "/";

        List<FileInfo> result = new ArrayList<>();

        try {
            ListObjectsV2Response resp = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .delimiter("/")
                            .build());

            for (CommonPrefix cp : resp.commonPrefixes()) {
                String dirKey = cp.prefix();
                String dirName = dirKey.substring(0, dirKey.length() - 1);
                dirName = dirName.substring(dirName.lastIndexOf('/') + 1);
                result.add(new FileInfo(dirName, toPath(dirKey), true, 0, 0));
            }

            resp.contents().stream()
                    .filter(obj -> !obj.key().equals(prefix))
                    .forEach(obj -> {
                        String name = obj.key();
                        int idx = name.lastIndexOf('/');
                        String fileName = idx >= 0 ? name.substring(idx + 1) : name;
                        result.add(new FileInfo(fileName, toPath(obj.key()), false,
                                obj.size(), obj.lastModified().toEpochMilli()));
                    });

        } catch (Exception e) {
            log.error("Failed to list S3 objects for path: {}", path, e);
        }

        return result;
    }

    @Override
    public InputStream readFile(String path) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(toKey(path)).build());
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public void writeFile(String path, InputStream data, long size) {
        try {
            byte[] bytes;
            if (size > 0) {
                bytes = data.readAllBytes();
            } else {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                data.transferTo(buf);
                bytes = buf.toByteArray();
            }

            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(toKey(path))
                            .acl(ObjectCannedACL.PRIVATE)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write S3 file: " + path, e);
        }
    }

    @Override
    public void deleteFile(String path) {
        String key = toKey(path);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket).key(key).build());
    }

    @Override
    public void createDirectory(String path) {
        String key = toKey(path);
        if (!key.isEmpty()) {
            String dirKey = key.endsWith("/") ? key : key + "/";
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(dirKey)
                            .build(),
                    RequestBody.empty());
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(toKey(path)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public FileInfo getFileInfo(String path) {
        String key = toKey(path);
        try {
            HeadObjectResponse resp = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(key).build());
            return new FileInfo(
                    key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key,
                    toPath(key), false, resp.contentLength(), resp.lastModified().toEpochMilli());
        } catch (NoSuchKeyException e) {
            String dirKey = key.isEmpty() ? "" : (key.endsWith("/") ? key : key + "/");
            try {
                ListObjectsV2Response listResp = s3Client.listObjectsV2(
                        ListObjectsV2Request.builder()
                                .bucket(bucket).prefix(dirKey).maxKeys(1).build());
                if (listResp.hasContents() || listResp.hasCommonPrefixes()) {
                    String name = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                    return new FileInfo(name.isEmpty() ? "/" : name, toPath(key), true, 0, 0);
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    @Override
    public void copyFile(String source, String dest) {
        s3Client.copyObject(b -> b
                .sourceBucket(bucket).sourceKey(toKey(source))
                .destinationBucket(bucket).destinationKey(toKey(dest)));
    }

    @Override
    public void moveFile(String source, String dest) {
        copyFile(source, dest);
        deleteFile(source);
    }

    @Override
    public String normalize(String path) {
        String clean = path.replace("\\", "/");
        if (!clean.startsWith("/")) clean = "/" + clean;
        if (clean.endsWith("/") && clean.length() > 1) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }
}
