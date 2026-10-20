package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/me")
@Profile("production")
public class TradeOrderLifecycleController {

    private final TradeOrderLifecycleService lifecycleService;

    public TradeOrderLifecycleController(TradeOrderLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    public ResponseEntity<Result<TradeOrderPageResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") int size) {
        if (buyerId == null || buyerId.isBlank()) {
            return response(HttpStatus.UNAUTHORIZED, Result.fail(401, "UNAUTHENTICATED"));
        }
        try {
            return response(HttpStatus.OK, Result.ok(lifecycleService.list(buyerId, page, size)));
        } catch (TradeOrderApiException error) {
            return response(HttpStatus.valueOf(error.status()), Result.fail(error.status(), error.errorCode()));
        } catch (DataAccessException | TransactionException unavailable) {
            return unavailable();
        }
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<Result<TradeOrderDetailResponse>> detail(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @PathVariable String orderNo) {
        if (buyerId == null || buyerId.isBlank()) {
            return response(HttpStatus.UNAUTHORIZED, Result.fail(401, "UNAUTHENTICATED"));
        }
        try {
            return response(HttpStatus.OK, Result.ok(lifecycleService.detail(buyerId, orderNo)));
        } catch (TradeOrderApiException error) {
            return response(HttpStatus.valueOf(error.status()), Result.fail(error.status(), error.errorCode()));
        } catch (DataAccessException | TransactionException unavailable) {
            return unavailable();
        }
    }

    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<Result<TradeOrderResponse>> cancel(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String orderNo) {
        if (buyerId == null || buyerId.isBlank()) {
            return response(HttpStatus.UNAUTHORIZED, Result.fail(401, "UNAUTHENTICATED"));
        }
        try {
            TradeOrderOutcome outcome = lifecycleService.cancel(buyerId, orderNo, idempotencyKey);
            if (!outcome.succeeded()) {
                return response(HttpStatus.valueOf(outcome.status()),
                        Result.fail(outcome.status(), outcome.errorCode()));
            }
            return response(HttpStatus.OK, Result.ok(outcome.order()));
        } catch (TradeOrderApiException error) {
            return response(HttpStatus.valueOf(error.status()), Result.fail(error.status(), error.errorCode()));
        } catch (DataAccessException | TransactionException unavailable) {
            return unavailable();
        }
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Object>> invalidParameter(MethodArgumentTypeMismatchException ignored) {
        return response(HttpStatus.BAD_REQUEST, Result.fail(400, "VALIDATION_FAILED"));
    }

    private static <T> ResponseEntity<Result<T>> unavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE, Result.fail(503, "TEMPORARY_UNAVAILABLE"));
    }

    private static <T> ResponseEntity<Result<T>> response(HttpStatus status, Result<T> body) {
        return ResponseEntity.status(status).body(body);
    }
}
