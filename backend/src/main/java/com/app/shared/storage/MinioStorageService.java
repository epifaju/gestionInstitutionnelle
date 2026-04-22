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
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService {

    public record Download(String objectName, String contentType, long size, InputStream stream) {}

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public void ensureBucket() throws Exception {
        String b = minioProperties.getBucket();
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(b).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(b).build());
        }
    }

    public void upload(String objectName, InputStream stream, long size, String contentType) throws Exception {
        ensureBucket();
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build());
    }

    public String presignedGetUrl(String objectName) throws Exception {
        String url =
                minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .expiry(7, TimeUnit.DAYS)
                        .build());
        String pub = minioProperties.getPublicEndpoint();
        if (pub == null || pub.isBlank()) {
            return url;
        }

        // IMPORTANT: preserve the query string EXACTLY (percent-encoding), otherwise the credential/signature breaks.
        // We therefore do not rebuild the URI via parsed components.
        String base = pub.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        int q = url.indexOf('?');
        String pathAndQuery =
                q >= 0
                        ? url.substring(url.indexOf('/', url.indexOf("://") + 3))
                        : url.substring(url.indexOf('/', url.indexOf("://") + 3));
        // pathAndQuery already begins with "/..."
        return base + pathAndQuery;
    }

    public List<String> listObjectNames(String prefix) throws Exception {
        ensureBucket();
        List<String> names = new ArrayList<>();
        Iterable<Result<Item>> results =
                minioClient.listObjects(ListObjectsArgs.builder().bucket(minioProperties.getBucket()).prefix(prefix).recursive(true).build());
        for (Result<Item> r : results) {
            names.add(r.get().objectName());
        }
        return names;
    }

    public Download download(String objectName) throws Exception {
        ensureBucket();
        var stat =
                minioClient.statObject(
                        StatObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
        InputStream stream =
                minioClient.getObject(
                        GetObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
        return new Download(objectName, stat.contentType(), stat.size(), stream);
    }

    public void delete(String objectName) throws Exception {
        ensureBucket();
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
    }
}
