package com.app.shared.storage;

import com.app.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock private MinioClient minioClient;
    @Mock private MinioProperties minioProperties;

    @InjectMocks private MinioStorageService service;

    @Test
    void ensureBucket_creeLeBucketQuandIlNexistePas() throws Exception {
        when(minioProperties.getBucket()).thenReturn("documents");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        service.ensureBucket();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void ensureBucket_neCreeRienQuandBucketExiste() throws Exception {
        when(minioProperties.getBucket()).thenReturn("documents");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.ensureBucket();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void upload_appellePutObjectAvecBucketEtObjectName() throws Exception {
        when(minioProperties.getBucket()).thenReturn("documents");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.upload("factures/FAC-2026-0001.pdf", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "application/pdf");

        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(cap.capture());
        PutObjectArgs args = cap.getValue();

        assertThat(args.bucket()).isEqualTo("documents");
        assertThat(args.object()).isEqualTo("factures/FAC-2026-0001.pdf");
        assertThat(args.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void presignedGetUrl_remplaceLeHostMaisConserveLeQueryString() throws Exception {
        when(minioProperties.getBucket()).thenReturn("documents");
        when(minioProperties.getPublicEndpoint()).thenReturn("https://cdn.example.com/");

        String minioUrl =
                "http://minio:9000/documents/factures/FAC-2026-0001.pdf"
                        + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc%2Fdef";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(minioUrl);

        String out = service.presignedGetUrl("factures/FAC-2026-0001.pdf");

        assertThat(out)
                .isEqualTo(
                        "https://cdn.example.com/documents/factures/FAC-2026-0001.pdf"
                                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc%2Fdef");
    }
}

