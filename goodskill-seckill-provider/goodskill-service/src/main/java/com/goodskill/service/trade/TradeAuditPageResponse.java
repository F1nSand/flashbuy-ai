package com.goodskill.service.trade;

import java.util.List;

public record TradeAuditPageResponse(long page, int size, long total, List<TradeAuditRecord> records) {
}
