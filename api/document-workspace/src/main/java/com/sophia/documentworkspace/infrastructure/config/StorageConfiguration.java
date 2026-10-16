package com.sophia.documentworkspace.infrastructure.config;

import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class StorageConfiguration {

    private final ApplicationProperties properties;

    @Bean
    public S3Client s3Client() {
        ApplicationProperties.Storage storage = properties.storage();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.region()))
                .credentialsProvider(credentialsProvider(storage))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyleAccess())
                        .build());
        if (storage.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(storage.endpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        ApplicationProperties.Storage storage = properties.storage();
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(storage.region()))
                .credentialsProvider(credentialsProvider(storage))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyleAccess())
                        .build());
        if (storage.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(storage.endpoint()));
        }
        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider(ApplicationProperties.Storage storage) {
        if (storage.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
