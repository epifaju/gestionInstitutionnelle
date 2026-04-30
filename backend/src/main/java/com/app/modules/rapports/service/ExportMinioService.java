package com.app.modules.rapports.service;

import com.app.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ExportMinioService {

    @Qualifier("internalMinioClient")
    private final MinioClient internalMinioClient;
    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;
    private final MinioProperties minioProperties;

    public void ensureBucket() throws Exception {
        String b = minioProperties.getBucket();
        if (!internalMinioClient.bucketExists(BucketExistsArgs.builder().bucket(b).build())) {
            internalMinioClient.makeBucket(MakeBucketArgs.builder().bucket(b).build());
        }
    }

    public void uploadBytes(String objectName, byte[] bytes, String contentType) throws Exception {
        ensureBucket();
        internalMinioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(contentType)
                        .build());
    }

    public String presignGet(String objectName, int expirySeconds) throws Exception {
        if (expirySeconds <= 0) expirySeconds = 3600;
        return publicMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .expiry(expirySeconds, TimeUnit.SECONDS)
                        .build());
    }

    public void deleteObject(String objectName) throws Exception {
        ensureBucket();
        internalMinioClient.removeObject(
                RemoveObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
    }
}

