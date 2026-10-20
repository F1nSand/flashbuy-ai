package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@Profile("production")
public class TradeOrderController {

    private final TradeOrderService tradeOrderService;

    public TradeOrderController(TradeOrderService tradeOrderService) {
        this.tradeOrderService = tradeOrderService;
    }

    @PostMapping("/{activityId}/orders")
    public ResponseEntity<Result<TradeOrderResponse>> create(
            @PathVariable long activityId,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTradeOrderRequest request) {
        if (buyerId == null || buyerId.isBlank()) {
            return response(HttpStatus.UNAUTHORIZED, Result.fail(401, "UNAUTHENTICATED"));
        }

        try {
            TradeOrderOutcome outcome = tradeOrderService.create(activityId, buyerId, idempotencyKey, request);
            if (!outcome.succeeded()) {
                return response(HttpStatus.valueOf(outcome.status()),
                        Result.fail(outcome.status(), outcome.errorCode()));
            }
            HttpStatus status = outcome.order().replayed() ? HttpStatus.OK : HttpStatus.CREATED;
            return response(status, Result.ok(outcome.order()));
        } catch (IllegalArgumentException invalidCommand) {
            return response(HttpStatus.BAD_REQUEST, Result.fail(400, "VALIDATION_FAILED"));
        } catch (DataAccessException unavailable) {
            return response(HttpStatus.SERVICE_UNAVAILABLE,
                    Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        } catch (TransactionException unavailable) {
            return response(HttpStatus.SERVICE_UNAVAILABLE,
                    Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<TradeOrderResponse>> invalidRequest(Exception ignored) {
        return response(HttpStatus.BAD_REQUEST, Result.fail(400, "VALIDATION_FAILED"));
    }

    private static ResponseEntity<Result<TradeOrderResponse>> response(
            HttpStatus status, Result<TradeOrderResponse> body) {
        return ResponseEntity.status(status).body(body);
    }
}
