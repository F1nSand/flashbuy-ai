package com.goodskill.service.trade;

public record TradeFileAsset(String fileId, String ownerId, String objectKey, String contentType,
                             long sizeBytes, String sha256, String visibility, String state) {
}
