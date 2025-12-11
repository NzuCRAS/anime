package com.anime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

/**
 * StorageConfig: 提供 S3Client 与 S3Presigner，支持 MinIO（path-style）
 */
@Configuration
public class StorageConfig {

    @Value("${storage.endpoint:}")
    private String storageEndpoint;

    @Value("${storage.region:us-east-1}")
    private String storageRegion;

    @Value("${storage.access-key:}")
    private String accessKey;

    @Value("${storage.secret-key:}")
    private String secretKey;

    /**
     * 选择凭证提供者：
     * - 若在配置文件中提供 accessKey & secretKey（非空），使用静态凭证（适合 MinIO）
     * - 否则回退到 DefaultCredentialsProvider（适合在 AWS 环境使用 IAM）
     */
    private static AwsCredentialsProvider chooseCredentialsProvider(String accessKey, String secretKey) {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(storageRegion))
                .credentialsProvider(chooseCredentialsProvider(accessKey, secretKey))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .build());

        if (storageEndpoint != null && !storageEndpoint.isBlank()) {
            // path-style for MinIO / local dev
            builder.endpointOverride(URI.create(storageEndpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(storageRegion))
                .credentialsProvider(chooseCredentialsProvider(accessKey, secretKey));

        if (storageEndpoint != null && !storageEndpoint.isBlank()) {
            // 同样确保 presigner 使用 path-style endpoint
            builder.endpointOverride(URI.create(storageEndpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}