package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/files")
@Profile("production")
@ConditionalOnProperty(prefix = "goodskill.files", name = "enabled", havingValue = "true")
public class TradeFileController {

    private final TradeFileAssetService fileAssetService;

    public TradeFileController(TradeFileAssetService fileAssetService) {
        this.fileAssetService = fileAssetService;
    }

    @PostMapping("/product-images")
    public ResponseEntity<Result<TradeFileUploadResponse>> uploadProductImage(
            @RequestHeader(value = "X-User-Id", required = false) String ownerId,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.status(201).body(Result.ok(fileAssetService.uploadProductImage(ownerId, file)));
        } catch (TradeFileApiException error) {
            return errorResponse(error);
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/public/{fileId}")
    public ResponseEntity<StreamingResponseBody> downloadPublicProductImage(@PathVariable String fileId) {
        try {
            TradeFileAsset asset = fileAssetService.requirePublicAsset(fileId);
            StreamingResponseBody body = output -> {
                try (var input = fileAssetService.openPublicAsset(asset)) {
                    input.transferTo(output);
                } catch (IOException streamFailure) {
                    throw new TradeFileApiException(503, "FILE_STORAGE_UNAVAILABLE");
                }
            };
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.contentType()))
                    .contentLength(asset.sizeBytes())
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(body);
        } catch (TradeFileApiException error) {
            return ResponseEntity.status(error.status()).build();
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteOwnedProductImage(
            @RequestHeader(value = "X-User-Id", required = false) String ownerId,
            @PathVariable String fileId) {
        try {
            fileAssetService.deleteOwned(ownerId, fileId);
            return ResponseEntity.noContent().build();
        } catch (TradeFileApiException error) {
            return errorResponse(error);
        }
    }

    @ExceptionHandler(TradeFileApiException.class)
    public ResponseEntity<Result<Void>> handleFileError(TradeFileApiException error) {
        return errorResponse(error);
    }

    private static <T> ResponseEntity<Result<T>> errorResponse(TradeFileApiException error) {
        return ResponseEntity.status(error.status()).body(Result.fail(error.status(), error.errorCode()));
    }
}
