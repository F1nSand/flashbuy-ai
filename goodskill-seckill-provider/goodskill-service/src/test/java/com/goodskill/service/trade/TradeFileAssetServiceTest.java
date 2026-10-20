package com.goodskill.service.trade;

import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeFileAssetServiceTest {

    private TradeFileAssetPersistence persistence;
    private MinioClient minioClient;

    @BeforeEach
    void setUp() {
        persistence = mock(TradeFileAssetPersistence.class);
        minioClient = mock(MinioClient.class);
    }

    @Test
    void uploadIgnoresClientFilenameAndContentTypeAndUsesValidatedImageBytes() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        TradeFileAssetService service = service(12_000_000);
        byte[] png = png(4, 3);

        TradeFileUploadResponse result = service.uploadProductImage("manager-1",
                new MockMultipartFile("file", "../../unsafe.html", "text/html", png));

        assertEquals("image/png", result.contentType());
        assertEquals(png.length, result.sizeBytes());
        org.mockito.ArgumentCaptor<TradeFileAsset> asset = org.mockito.ArgumentCaptor.forClass(TradeFileAsset.class);
        verify(persistence).beginUpload(asset.capture(), eq(52_428_800L));
        assertEquals("manager-1", asset.getValue().ownerId());
        assertEquals("PRODUCT_PUBLIC", asset.getValue().visibility());
        org.junit.jupiter.api.Assertions.assertTrue(asset.getValue().objectKey().matches(
                "product-images/[0-9a-f]{32}\\.png"));
        verify(persistence).activate(result.fileId());
    }

    @Test
    void fakeImageBytesAreRejectedBeforeObjectStorageOrMetadataWrites() {
        TradeFileAssetService service = service(12_000_000);

        TradeFileApiException error = assertThrows(TradeFileApiException.class,
                () -> service.uploadProductImage("manager-1",
                        new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));

        assertEquals("FILE_TYPE_INVALID", error.errorCode());
        verifyNoInteractions(persistence, minioClient);
    }

    @Test
    void excessivePixelCountIsRejectedBeforeStorage() {
        TradeFileAssetService service = service(100);

        TradeFileApiException error = assertThrows(TradeFileApiException.class,
                () -> service.uploadProductImage("manager-1",
                        new MockMultipartFile("file", "large.png", "image/png", png(11, 10))));

        assertEquals("FILE_DIMENSIONS_INVALID", error.errorCode());
        verifyNoInteractions(persistence, minioClient);
    }

    @Test
    void objectIsCompensatedWhenMetadataActivationFails() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        doThrow(new IllegalStateException("database unavailable")).when(persistence).activate(anyString());
        TradeFileAssetService service = service(12_000_000);

        TradeFileApiException error = assertThrows(TradeFileApiException.class,
                () -> service.uploadProductImage("manager-1",
                        new MockMultipartFile("file", "item.png", "image/png", png(2, 2))));

        assertEquals("FILE_STORAGE_UNAVAILABLE", error.errorCode());
        verify(minioClient).removeObject(any(io.minio.RemoveObjectArgs.class));
        verify(persistence).failUpload(anyString());
    }

    @Test
    void fileLargerThanHardLimitIsRejectedWithoutReadingOrStoring() {
        TradeFileAssetService service = service(12_000_000);

        TradeFileApiException error = assertThrows(TradeFileApiException.class,
                () -> service.uploadProductImage("manager-1",
                        new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])));

        assertEquals("FILE_SIZE_INVALID", error.errorCode());
        verifyNoInteractions(persistence, minioClient);
        verify(persistence, never()).activate(anyString());
    }

    @Test
    void cleanupDeletesOnlyAfterItClaimsTheStaleAsset() throws Exception {
        TradeFileAsset candidate = new TradeFileAsset("0123456789abcdef0123456789abcdef", "manager-1",
                "product-images/0123456789abcdef0123456789abcdef.png", "image/png", 64,
                "a".repeat(64), "PRODUCT_PUBLIC", "DELETED");
        when(persistence.findCleanupCandidates(100)).thenReturn(List.of(candidate));
        when(persistence.claimCleanupCandidate(candidate.fileId())).thenReturn(false);

        int removed = service(12_000_000).cleanupOrphans();

        assertEquals(0, removed);
        verify(minioClient, never()).removeObject(any(io.minio.RemoveObjectArgs.class));
        verify(persistence, never()).removeCleanupCandidate(anyString());
    }

    @Test
    void cleanupRemovesClaimedObjectAndThenItsMetadata() throws Exception {
        TradeFileAsset candidate = new TradeFileAsset("0123456789abcdef0123456789abcdef", "manager-1",
                "product-images/0123456789abcdef0123456789abcdef.png", "image/png", 64,
                "a".repeat(64), "PRODUCT_PUBLIC", "DELETED");
        when(persistence.findCleanupCandidates(100)).thenReturn(List.of(candidate));
        when(persistence.claimCleanupCandidate(candidate.fileId())).thenReturn(true);

        int removed = service(12_000_000).cleanupOrphans();

        assertEquals(1, removed);
        verify(minioClient).removeObject(any(io.minio.RemoveObjectArgs.class));
        verify(persistence).removeCleanupCandidate(candidate.fileId());
    }

    private TradeFileAssetService service(long maxPixels) {
        return new TradeFileAssetService(persistence, minioClient, "private-images", 5 * 1024 * 1024,
                maxPixels, 52_428_800);
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }
}
