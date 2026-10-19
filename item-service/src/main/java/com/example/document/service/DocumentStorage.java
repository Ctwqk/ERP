package com.example.document.service;

import com.example.document.config.MinioProps;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Thin wrapper around the MinIO client so the document services can be tested without an object store. */
@Component
public class DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorage.class);

    private final MinioClient client;
    private final MinioClient presigner;
    private final MinioProps props;

    public DocumentStorage(@Qualifier("minioClient") MinioClient client,
            @Qualifier("presignClient") MinioClient presigner, MinioProps props) {
        this.client = client;
        this.presigner = presigner;
        this.props = props;
    }

    public String bucket() {
        return props.bucket();
    }

    /** Creates the configured bucket if it does not exist yet. */
    public void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).region(props.region()).build());
                log.info("Created bucket {}", props.bucket());
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage is unavailable", e);
        }
    }

    public String presignedPutUrl(String bucket, String objectKey, int expirySeconds) {
        return presign(Method.PUT, bucket, objectKey, expirySeconds);
    }

    public String presignedGetUrl(String bucket, String objectKey, int expirySeconds) {
        return presign(Method.GET, bucket, objectKey, expirySeconds);
    }

    private String presign(Method method, String bucket, String objectKey, int expirySeconds) {
        try {
            return presigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method).bucket(bucket).object(objectKey).expiry(expirySeconds).build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not presign URL for " + objectKey, e);
        }
    }

    /** Size/etag of an uploaded object, or empty if nothing was uploaded under that key. */
    public Optional<StatObjectResponse> stat(String bucket, String objectKey) {
        try {
            return Optional.of(client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build()));
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code)) {
                return Optional.empty();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Object storage error: " + code, e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage is unavailable", e);
        }
    }

    public InputStream open(String bucket, String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read " + objectKey, e);
        }
    }

    public void remove(String bucket, String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.warn("Could not remove object {}/{}", bucket, objectKey, e);
        }
    }
}
