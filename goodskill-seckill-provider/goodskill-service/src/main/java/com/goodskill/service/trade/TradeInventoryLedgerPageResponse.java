package com.goodskill.service.trade;

import java.util.List;

public record TradeInventoryLedgerPageResponse(long page, int size, long total,
                                                List<TradeInventoryLedgerRecord> records) {
}
