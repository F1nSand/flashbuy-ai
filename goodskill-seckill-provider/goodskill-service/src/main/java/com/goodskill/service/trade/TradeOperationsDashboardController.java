package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage/metrics")
@Profile("production")
public class TradeOperationsDashboardController {
    private final TradeOperationsService operationsService;

    public TradeOperationsDashboardController(TradeOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping
    public ResponseEntity<Result<TradeOperationsMetrics>> metrics() {
        try {
            return ResponseEntity.ok(Result.ok(operationsService.metrics()));
        } catch (DataAccessException | TransactionException unavailable) {
            return ResponseEntity.status(503).body(Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }
}
