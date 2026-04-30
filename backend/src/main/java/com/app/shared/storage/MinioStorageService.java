package com.app.shared.storage;

import com.app.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService {

    public record Download(String objectName, String contentType, long size, InputStream stream) {}

    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final MinioProperties minioProperties;

    public MinioStorageService(
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient,
            MinioProperties minioProperties) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.minioProperties = minioProperties;
    }

    public void ensureBucket() throws Exception {
        String b = minioProperties.getBucket();
        if (!internalMinioClient.bucketExists(BucketExistsArgs.builder().bucket(b).build())) {
            internalMinioClient.makeBucket(MakeBucketArgs.builder().bucket(b).build());
        }
    }

    public void upload(String objectName, InputStream stream, long size, String contentType) throws Exception {
        ensureBucket();
        internalMinioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build());
    }

    public String presignedGetUrl(String objectName) throws Exception {
        // Security default: short-lived URL to reduce accidental leakage impact.
        // Call presignedGetUrl(objectName, expirySeconds) explicitly for longer TTL when justified.
        return presignedGetUrl(objectName, 3600);
    }

    public String presignedGetUrl(String objectName, int expirySeconds) throws Exception {
        if (expirySeconds <= 0) {
            expirySeconds = 3600;
        }
        return publicMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .expiry(expirySeconds, TimeUnit.SECONDS)
                        .build());
    }

    public List<String> listObjectNames(String prefix) throws Exception {
        ensureBucket();
        List<String> names = new ArrayList<>();
        Iterable<Result<Item>> results =
                internalMinioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(minioProperties.getBucket())
                                .prefix(prefix)
                                .recursive(true)
                                .build());
        for (Result<Item> r : results) {
            names.add(r.get().objectName());
        }
        return names;
    }

    public Download download(String objectName) throws Exception {
        ensureBucket();
        var stat =
                internalMinioClient.statObject(
                        StatObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
        InputStream stream =
                internalMinioClient.getObject(
                        GetObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
        return new Download(objectName, stat.contentType(), stat.size(), stream);
    }

    public void delete(String objectName) throws Exception {
        ensureBucket();
        internalMinioClient.removeObject(
                RemoveObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
    }
}
