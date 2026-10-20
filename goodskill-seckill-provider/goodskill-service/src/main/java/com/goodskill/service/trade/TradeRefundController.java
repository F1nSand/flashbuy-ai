package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage")
@Profile("production")
public class TradeRefundController {

    private final TradePaymentService paymentService;

    public TradeRefundController(TradePaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderNo}/refund")
    public ResponseEntity<Result<TradeRefundResponse>> requestFullRefund(
            @RequestHeader(value = "X-User-Id", required = false) String actorId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String orderNo,
            @Valid @RequestBody TradeRefundRequest request) {
        if (actorId == null || actorId.isBlank()) {
            return response(HttpStatus.UNAUTHORIZED, Result.fail(401, "UNAUTHENTICATED"));
        }
        try {
            TradeOrderLifecycleService.RefundPlan plan = paymentService.requestFullRefund(
                    actorId, orderNo, idempotencyKey, request.reason());
            return response(HttpStatus.ACCEPTED, Result.ok(new TradeRefundResponse(plan.refundNo(), plan.orderNo(),
                    plan.amountMinor(), "CNY", plan.state())));
        } catch (TradeOrderApiException error) {
            return response(HttpStatus.valueOf(error.status()), Result.fail(error.status(), error.errorCode()));
        } catch (DataAccessException | TransactionException unavailable) {
            return response(HttpStatus.SERVICE_UNAVAILABLE, Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }

    private static <T> ResponseEntity<Result<T>> response(HttpStatus status, Result<T> body) {
        return ResponseEntity.status(status).body(body);
    }
}
