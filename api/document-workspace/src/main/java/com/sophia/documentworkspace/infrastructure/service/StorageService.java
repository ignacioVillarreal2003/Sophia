package com.sophia.documentworkspace.infrastructure.service;

import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.exception.ApiException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Thin, exception-translating wrapper around the S3 API (AWS or MinIO).
 * All object keys are produced by the calling services; this class never
 * invents keys and never touches the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ApplicationProperties properties;

    private String bucket() {
        return properties.storage().bucket();
    }

    @PostConstruct
    void ensureBucketExists() {
        String bucket = bucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created storage bucket {}", bucket);
            } catch (S3Exception creationFailure) {
                log.warn("Unable to create storage bucket {}: {}", bucket, creationFailure.getMessage());
            }
        } catch (Exception e) {
            log.warn("Unable to verify storage bucket {}: {}", bucket, e.getMessage());
        }
    }

    public void putObject(String key, InputStream content, long contentLength, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(content, contentLength));
        } catch (S3Exception e) {
            throw uploadFailure("Failed to store object", e);
        }
    }

    public void putObject(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw uploadFailure("Failed to store object", e);
        }
    }

    public InputStream getObject(String key) {
        try {
            return s3Client.getObject(GetObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Stored object not found: " + key);
        } catch (S3Exception e) {
            throw uploadFailure("Failed to read object", e);
        }
    }

    /** Size in bytes of a stored object; null when it does not exist. */
    public Long objectSize(String key) {
        try {
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket()).key(key).build());
            return head.contentLength();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            throw uploadFailure("Failed to stat object", e);
        }
    }

    /** Best-effort delete used by trash purging; a miss is not an error. */
    public void deleteObjectQuietly(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (S3Exception e) {
            log.warn("Failed to delete storage object {}: {}", key, e.getMessage());
        }
    }

    public URL presignedDownloadUrl(String key, String downloadFilename) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket())
                .key(key)
                .responseContentDisposition(downloadFilename == null ? null
                        : "attachment; filename=\"" + downloadFilename.replace("\"", "") + "\"")
                .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.storage().presignedUrlTtl())
                        .getObjectRequest(get)
                        .build())
                .url();
    }

    // ------------------------------------------------------------------
    // Multipart
    // ------------------------------------------------------------------

    public String initiateMultipartUpload(String key, String contentType) {
        try {
            return s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                            .bucket(bucket())
                            .key(key)
                            .contentType(contentType)
                            .build())
                    .uploadId();
        } catch (S3Exception e) {
            throw uploadFailure("Failed to initiate multipart upload", e);
        }
    }

    /** Presigned URL the client PUTs one part to. */
    public URL presignedPartUrl(String key, String uploadId, int partNumber) {
        UploadPartRequest part = UploadPartRequest.builder()
                .bucket(bucket())
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        return s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                        .signatureDuration(properties.storage().presignedUrlTtl())
                        .uploadPartRequest(part)
                        .build())
                .url();
    }

    /** @param partEtags partNumber → ETag returned by S3 for that part. */
    public void completeMultipartUpload(String key, String uploadId, Map<Integer, String> partEtags) {
        List<CompletedPart> parts = partEtags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> CompletedPart.builder()
                        .partNumber(entry.getKey())
                        .eTag(entry.getValue())
                        .build())
                .toList();
        try {
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
        } catch (S3Exception e) {
            throw uploadFailure("Failed to complete multipart upload", e);
        }
    }

    public void abortMultipartUploadQuietly(String key, String uploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .uploadId(uploadId)
                    .build());
        } catch (S3Exception e) {
            log.warn("Failed to abort multipart upload {} on {}: {}", uploadId, key, e.getMessage());
        }
    }

    private ApiException uploadFailure(String message, S3Exception cause) {
        return new ApiException(ErrorCode.UPLOAD_FAILED, HttpStatus.BAD_GATEWAY,
                message, Map.of("s3Error", cause.awsErrorDetails() == null
                        ? cause.getMessage()
                        : cause.awsErrorDetails().errorMessage()), cause);
    }
}
