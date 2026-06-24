package com.filesever.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.obs.services.ObsClient;
import com.obs.services.model.DeleteObjectRequest;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectRequest;

public class ObsFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(ObsFileStorage.class);

    private final ObsClient obsClient;
    private final String bucket;

    public ObsFileStorage(ObsClient obsClient, String bucket) {
        this.obsClient = obsClient;
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
            ListObjectsRequest request = new ListObjectsRequest(bucket);
            request.setPrefix(prefix);
            request.setDelimiter("/");

            ObjectListing listing = obsClient.listObjects(request);

            for (String dirKey : listing.getCommonPrefixes()) {
                String dirName = dirKey.substring(0, dirKey.length() - 1);
                dirName = dirName.substring(dirName.lastIndexOf('/') + 1);
                result.add(new FileInfo(dirName, toPath(dirKey), true, 0, 0));
            }

            for (ObsObject obj : listing.getObjects()) {
                String key = obj.getObjectKey();
                if (key.equals(prefix)) continue;
                String name = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                result.add(new FileInfo(name, toPath(key), false,
                        obj.getMetadata().getContentLength(),
                        obj.getMetadata().getLastModified().getTime()));
            }

        } catch (Exception e) {
            log.error("Failed to list OBS objects for path: {}", path, e);
        }

        return result;
    }

    @Override
    public InputStream readFile(String path) {
        try {
            return obsClient.getObject(bucket, toKey(path)).getObjectContent();
        } catch (Exception e) {
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

            obsClient.putObject(new PutObjectRequest(bucket, toKey(path),
                    new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write OBS file: " + path, e);
        }
    }

    @Override
    public void deleteFile(String path) {
        obsClient.deleteObject(new DeleteObjectRequest(bucket, toKey(path)));
    }

    @Override
    public void createDirectory(String path) {
        String key = toKey(path);
        if (!key.isEmpty()) {
            String dirKey = key.endsWith("/") ? key : key + "/";
            obsClient.putObject(new PutObjectRequest(bucket, dirKey, new ByteArrayInputStream(new byte[0])));
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            return obsClient.doesObjectExist(bucket, toKey(path));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public FileInfo getFileInfo(String path) {
        String key = toKey(path);
        try {
            ObsObject obj = obsClient.getObject(bucket, key);
            return new FileInfo(
                    key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key,
                    toPath(key), false,
                    obj.getMetadata().getContentLength(),
                    obj.getMetadata().getLastModified().getTime());
        } catch (Exception e) {
            String dirKey = key.isEmpty() ? "" : (key.endsWith("/") ? key : key + "/");
            try {
                ListObjectsRequest req = new ListObjectsRequest(bucket);
                req.setPrefix(dirKey);
                req.setMaxKeys(1);
                ObjectListing listing = obsClient.listObjects(req);
                if (!listing.getObjects().isEmpty() || !listing.getCommonPrefixes().isEmpty()) {
                    String name = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                    return new FileInfo(name.isEmpty() ? "/" : name, toPath(key), true, 0, 0);
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    @Override
    public void copyFile(String source, String dest) {
        obsClient.copyObject(bucket, toKey(source), bucket, toKey(dest));
    }

    @Override
    public void moveFile(String source, String dest) {
        obsClient.copyObject(bucket, toKey(source), bucket, toKey(dest));
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
