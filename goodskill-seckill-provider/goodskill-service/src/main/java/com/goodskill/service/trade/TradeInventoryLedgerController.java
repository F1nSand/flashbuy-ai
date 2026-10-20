package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage/inventory")
@Profile("production")
public class TradeInventoryLedgerController {
    private final TradeInventoryLedgerService ledgerService;

    public TradeInventoryLedgerController(TradeInventoryLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public ResponseEntity<Result<TradeInventoryLedgerPageResponse>> list(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            return ResponseEntity.ok(Result.ok(ledgerService.list(activityId, skuId, orderNo, page, size)));
        } catch (TradeOrderApiException error) {
            return ResponseEntity.status(error.status()).body(Result.fail(error.status(), error.errorCode()));
        } catch (DataAccessException | TransactionException unavailable) {
            return ResponseEntity.status(503).body(Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }
}
