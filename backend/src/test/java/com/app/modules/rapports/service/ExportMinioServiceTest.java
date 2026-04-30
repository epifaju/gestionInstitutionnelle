package com.app.modules.rapports.service;

import com.app.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportMinioServiceTest {

    @Mock
    private MinioClient internalMinioClient;
    @Mock
    private MinioClient publicMinioClient;
    @Mock
    private MinioProperties minioProperties;

    @Test
    void uploadBytes_creeBucketSiAbsentPuisPutObject() throws Exception {
        when(minioProperties.getBucket()).thenReturn("bucket");
        when(internalMinioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        ExportMinioService svc = new ExportMinioService(internalMinioClient, publicMinioClient, minioProperties);
        svc.uploadBytes("a/b.pdf", new byte[] {1, 2, 3}, "application/pdf");

        verify(internalMinioClient).makeBucket(any(MakeBucketArgs.class));
        verify(internalMinioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadBytes_neCreePasBucketSiPresent() throws Exception {
        when(minioProperties.getBucket()).thenReturn("bucket");
        when(internalMinioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        ExportMinioService svc = new ExportMinioService(internalMinioClient, publicMinioClient, minioProperties);
        svc.uploadBytes("a/b.pdf", new byte[] {1}, "application/pdf");

        verify(internalMinioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(internalMinioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void presignGet_utiliseClientPublic() throws Exception {
        when(minioProperties.getBucket()).thenReturn("bucket");
        when(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://signed");

        ExportMinioService svc = new ExportMinioService(internalMinioClient, publicMinioClient, minioProperties);
        svc.presignGet("x.pdf", 3600);

        verify(publicMinioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void deleteObject_appelleRemoveObject() throws Exception {
        when(minioProperties.getBucket()).thenReturn("bucket");
        when(internalMinioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        ExportMinioService svc = new ExportMinioService(internalMinioClient, publicMinioClient, minioProperties);
        svc.deleteObject("x.pdf");

        verify(internalMinioClient).removeObject(any(RemoveObjectArgs.class));
    }
}

