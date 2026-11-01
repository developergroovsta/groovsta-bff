package com.groovsta.shared.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfiguration {
    @Bean
    S3Client groovstaS3Client(S3StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());
        if (properties.getEndpoint() != null) builder.endpointOverride(properties.getEndpoint());
        return builder.build();
    }

    @Bean
    S3Presigner groovstaS3Presigner(S3StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());
        if (properties.getEndpoint() != null) builder.endpointOverride(properties.getEndpoint());
        return builder.build();
    }

    @Bean
    ObjectStorageService objectStorageService(
            S3Client s3Client,
            S3Presigner presigner,
            S3StorageProperties properties
    ) {
        return new S3ObjectStorageService(s3Client, presigner, properties);
    }
}
