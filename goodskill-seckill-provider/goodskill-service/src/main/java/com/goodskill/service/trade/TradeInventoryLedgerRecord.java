package com.goodskill.service.trade;

import java.time.LocalDateTime;

public record TradeInventoryLedgerRecord(long ledgerId, long activityId, long skuId, String orderNo,
        String actorId, String operationType, long availableDelta, long reservedDelta, long soldDelta,
        String reason, LocalDateTime createdAt) {
}
