package com.groovsta.shared.storage;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

final class S3ObjectStorageService implements ObjectStorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final S3StorageProperties properties;

    S3ObjectStorageService(S3Client client, S3Presigner presigner, S3StorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUpload createUpload(String objectKey, String contentType, Duration validity) {
        var request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(validity)
                .putObjectRequest(request)
                .build());
        return new PresignedUpload(objectKey, signed.url().toString(), signed.expiration());
    }

    @Override
    public String createDownloadUrl(String objectKey, Duration validity) {
        var request = GetObjectRequest.builder().bucket(properties.getBucket()).key(objectKey).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(request)
                .build()).url().toString();
    }

    @Override
    public Optional<StoredObjectMetadata> metadata(String objectKey) {
        try {
            var response = client.headObject(
                    HeadObjectRequest.builder().bucket(properties.getBucket()).key(objectKey).build()
            );
            return Optional.of(new StoredObjectMetadata(response.contentLength(), response.contentType()));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return metadata(objectKey).isPresent();
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build());
    }
}
