package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage/audit")
@Profile("production")
public class TradeAuditController {

    private final TradeAuditService auditService;

    public TradeAuditController(TradeAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<Result<TradeAuditPageResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") int size) {
        if (callerId == null || callerId.isBlank()) {
            return ResponseEntity.status(401).body(Result.fail(401, "UNAUTHENTICATED"));
        }
        try {
            return ResponseEntity.ok(Result.ok(auditService.list(actorId, action, targetType, targetId, page, size)));
        } catch (TradeOrderApiException error) {
            return ResponseEntity.status(error.status()).body(Result.fail(error.status(), error.errorCode()));
        } catch (DataAccessException | TransactionException unavailable) {
            return ResponseEntity.status(503).body(Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }
}
