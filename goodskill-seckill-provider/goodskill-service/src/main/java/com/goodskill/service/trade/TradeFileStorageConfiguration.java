package com.goodskill.service.trade;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;

@Configuration
@Profile("production")
@ConditionalOnProperty(prefix = "goodskill.files", name = "enabled", havingValue = "true")
public class TradeFileStorageConfiguration {

    @Bean("tradeFileMinioClient")
    public MinioClient tradeFileMinioClient(
            @Value("${GOODSKILL_MINIO_ENDPOINT:}") String endpoint,
            @Value("${GOODSKILL_MINIO_ACCESS_KEY:}") String accessKey,
            @Value("${GOODSKILL_MINIO_SECRET_KEY:}") String secretKey,
            @Value("${GOODSKILL_MINIO_BUCKET:}") String bucket) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException invalidEndpoint) {
            throw new IllegalStateException("GOODSKILL_MINIO_ENDPOINT must be a valid HTTPS URL", invalidEndpoint);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank()) {
            throw new IllegalStateException("Production file storage requires an HTTPS endpoint, bucket and credentials");
        }
        MinioClient client = MinioClient.builder().endpoint(uri.toString())
                .credentials(accessKey, secretKey).build();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                throw new IllegalStateException("Configured production file bucket does not exist");
            }
        } catch (Exception unavailable) {
            throw new IllegalStateException("Configured production file storage is unavailable", unavailable);
        }
        return client;
    }
}
