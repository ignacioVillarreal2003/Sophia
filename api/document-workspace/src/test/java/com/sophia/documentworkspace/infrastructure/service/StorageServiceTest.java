package com.sophia.documentworkspace.infrastructure.service;

import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties(null,
                new ApplicationProperties.Storage("test-bucket", null, "us-east-1", null, null, true,
                        Duration.ofMinutes(15), DataSize.ofMegabytes(5)),
                null, null, null, null, null, null);
        storageService = new StorageService(s3Client, s3Presigner, properties);
    }

    @Test
    @DisplayName("an existing bucket is left untouched")
    void doesNotCreateAnExistingBucket() {
        storageService.ensureBucketExists();

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("a missing bucket is created")
    void createsAMissingBucket() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no bucket").build());

        storageService.ensureBucketExists();

        verify(s3Client).createBucket(CreateBucketRequest.builder().bucket("test-bucket").build());
    }

    @Test
    @DisplayName("a failure while creating the missing bucket is logged, not thrown")
    void bucketCreationFailureIsSwallowed() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no bucket").build());
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .awsErrorDetails(AwsErrorDetails.builder().errorMessage("forbidden").build())
                        .build());

        assertThatCode(() -> storageService.ensureBucketExists()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unexpected failure while checking the bucket is logged, not thrown")
    void bucketCheckFailureIsSwallowed() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("timeout").build());

        assertThatCode(() -> storageService.ensureBucketExists()).doesNotThrowAnyException();
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }
}
