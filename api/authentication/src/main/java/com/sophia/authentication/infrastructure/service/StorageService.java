package com.sophia.authentication.infrastructure.service;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class StorageService {

    public record StoredObject(byte[] content, String contentType) {
    }

    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final S3Client s3Client;
    private final ApplicationProperties properties;

    public StorageService(S3Client s3Client, ApplicationProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucketExists() {
        String bucket = properties.storage().bucket();
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

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !properties.storage().allowedImageTypes().contains(contentType)) {
            throw new BadRequestException("Unsupported image type. Allowed: "
                    + String.join(", ", properties.storage().allowedImageTypes()));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
        String detectedType = detectImageType(content);
        if (detectedType == null || !properties.storage().allowedImageTypes().contains(detectedType)) {
            throw new BadRequestException("File content is not a supported image. Allowed: "
                    + String.join(", ", properties.storage().allowedImageTypes()));
        }
        String filename = UUID.randomUUID() + EXTENSION_BY_TYPE.getOrDefault(detectedType, ".bin");
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.storage().bucket())
                            .key(filename)
                            .contentType(detectedType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
        return filename;
    }

    public StoredObject load(String filename) {
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.storage().bucket())
                    .key(filename)
                    .build());
            return new StoredObject(object.asByteArray(), object.response().contentType());
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("File", filename);
        } catch (S3Exception e) {
            throw new IllegalStateException("Failed to read stored file", e);
        }
    }

    public void delete(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.storage().bucket())
                    .key(filename)
                    .build());
        } catch (S3Exception e) {
            log.warn("Failed to delete stored file {}: {}", filename, e.getMessage());
        }
    }

    static String detectImageType(byte[] content) {
        if (content == null || content.length < 12) {
            return null;
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && (content[4] & 0xFF) == 0x0D && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A && (content[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
