package com.goodskill.service.trade;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Service
@Profile("production")
@ConditionalOnProperty(prefix = "goodskill.files", name = "enabled", havingValue = "true")
public class TradeFileAssetService {

    private static final Logger log = LoggerFactory.getLogger(TradeFileAssetService.class);
    private static final long HARD_MAX_BYTES = 5L * 1024 * 1024;
    private static final long HARD_MAX_PIXELS = 12_000_000L;
    private static final String PRODUCT_PUBLIC = "PRODUCT_PUBLIC";

    private final TradeFileAssetPersistence persistence;
    private final MinioClient minioClient;
    private final String bucket;
    private final long maxBytes;
    private final long maxPixels;
    private final long ownerQuotaBytes;

    public TradeFileAssetService(TradeFileAssetPersistence persistence,
                                 @Qualifier("tradeFileMinioClient") MinioClient minioClient,
                                 @Value("${GOODSKILL_MINIO_BUCKET:}") String bucket,
                                 @Value("${goodskill.files.max-file-bytes:5242880}") long maxBytes,
                                 @Value("${goodskill.files.max-pixels:12000000}") long maxPixels,
                                 @Value("${goodskill.files.owner-quota-bytes:52428800}") long ownerQuotaBytes) {
        if (!StringUtils.hasText(bucket) || maxBytes < 1 || maxBytes > HARD_MAX_BYTES
                || maxPixels < 1 || maxPixels > HARD_MAX_PIXELS || ownerQuotaBytes < maxBytes) {
            throw new IllegalStateException("Production file storage bucket and bounded limits are required");
        }
        this.persistence = persistence;
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
        this.maxPixels = maxPixels;
        this.ownerQuotaBytes = ownerQuotaBytes;
    }

    public TradeFileUploadResponse uploadProductImage(String ownerId, MultipartFile file) {
        if (!StringUtils.hasText(ownerId) || ownerId.length() > 64) {
            throw new TradeFileApiException(401, "UNAUTHENTICATED");
        }
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) {
            throw new TradeFileApiException(413, "FILE_SIZE_INVALID");
        }
        byte[] content;
        String contentType;
        try {
            content = file.getBytes();
            if (content.length == 0 || content.length > maxBytes) {
                throw new TradeFileApiException(413, "FILE_SIZE_INVALID");
            }
            contentType = validateImage(content);
        } catch (IOException unreadable) {
            throw new TradeFileApiException(400, "FILE_CONTENT_INVALID");
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");
        String extension = "image/png".equals(contentType) ? "png" : "jpg";
        String objectKey = "product-images/" + fileId + "." + extension;
        TradeFileAsset asset = new TradeFileAsset(fileId, ownerId, objectKey, contentType, content.length,
                sha256(content), PRODUCT_PUBLIC, "UPLOADING");
        persistence.beginUpload(asset, ownerQuotaBytes);
        try {
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType).build());
            persistence.activate(fileId);
        } catch (Exception storageOrMetadataFailure) {
            removeObjectQuietly(objectKey);
            try {
                persistence.failUpload(fileId);
            } catch (RuntimeException compensationFailure) {
                storageOrMetadataFailure.addSuppressed(compensationFailure);
            }
            log.warn("Product image upload failed; assetId={}", fileId);
            throw new TradeFileApiException(503, "FILE_STORAGE_UNAVAILABLE");
        }
        return new TradeFileUploadResponse(fileId, contentType, content.length);
    }

    public TradeFileAsset requirePublicAsset(String fileId) {
        validateFileId(fileId);
        TradeFileAsset asset = persistence.findPublicAsset(fileId);
        if (asset == null) {
            throw new TradeFileApiException(404, "FILE_NOT_FOUND");
        }
        return asset;
    }

    public InputStream openPublicAsset(TradeFileAsset asset) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(asset.objectKey()).build());
        } catch (Exception storageFailure) {
            throw new TradeFileApiException(503, "FILE_STORAGE_UNAVAILABLE");
        }
    }

    public void deleteOwned(String ownerId, String fileId) {
        if (!StringUtils.hasText(ownerId) || ownerId.length() > 64) {
            throw new TradeFileApiException(401, "UNAUTHENTICATED");
        }
        validateFileId(fileId);
        TradeFileAsset asset = persistence.findPublicAsset(fileId);
        if (asset == null || !ownerId.equals(asset.ownerId())) {
            throw new TradeFileApiException(404, "FILE_NOT_FOUND");
        }
        if (!persistence.markDeletedByOwner(fileId, ownerId)) {
            throw new TradeFileApiException(404, "FILE_NOT_FOUND");
        }
        removeObjectQuietly(asset.objectKey());
    }

    public int cleanupOrphans() {
        List<TradeFileAsset> candidates = persistence.findCleanupCandidates(100);
        int cleaned = 0;
        for (TradeFileAsset asset : candidates) {
            try {
                if (!persistence.claimCleanupCandidate(asset.fileId())) {
                    continue;
                }
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(asset.objectKey()).build());
                persistence.removeCleanupCandidate(asset.fileId());
                cleaned++;
            } catch (Exception cleanupFailure) {
                log.warn("Orphan file cleanup failed; assetId={}", asset.fileId());
            }
        }
        return cleaned;
    }

    private String validateImage(byte[] content) {
        String expectedFormat;
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G' && content[4] == 0x0d && content[5] == 0x0a
                && content[6] == 0x1a && content[7] == 0x0a) {
            expectedFormat = "png";
        } else if (content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            expectedFormat = "jpeg";
        } else {
            throw new TradeFileApiException(400, "FILE_TYPE_INVALID");
        }
        try (ImageInputStream image = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (image == null) {
                throw new TradeFileApiException(400, "FILE_CONTENT_INVALID");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) {
                throw new TradeFileApiException(400, "FILE_CONTENT_INVALID");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(image, true, true);
                if (!expectedFormat.equals(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) {
                    throw new TradeFileApiException(400, "FILE_TYPE_INVALID");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > maxPixels) {
                    throw new TradeFileApiException(413, "FILE_DIMENSIONS_INVALID");
                }
                if (reader.read(0) == null) {
                    throw new TradeFileApiException(400, "FILE_CONTENT_INVALID");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException malformedImage) {
            throw new TradeFileApiException(400, "FILE_CONTENT_INVALID");
        }
        return "png".equals(expectedFormat) ? "image/png" : "image/jpeg";
    }

    private void removeObjectQuietly(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception cleanupFailure) {
            log.warn("Object storage compensation pending; objectKey={}", objectKey);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void validateFileId(String fileId) {
        if (fileId == null || !fileId.matches("[0-9a-f]{32}")) {
            throw new TradeFileApiException(404, "FILE_NOT_FOUND");
        }
    }
}
