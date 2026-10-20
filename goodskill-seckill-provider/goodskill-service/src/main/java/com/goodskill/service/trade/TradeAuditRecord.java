package com.goodskill.service.trade;

import java.time.LocalDateTime;

public record TradeAuditRecord(long auditId, String actorId, String action, String targetType,
                               String targetId, String requestId, String result, String reason,
                               String changesJson, LocalDateTime createdAt) {
}
