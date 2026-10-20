package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/manage/orders")
@Profile("production")
public class TradeOperationsController {
    private final TradeOperationsService operationsService;

    public TradeOperationsController(TradeOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping
    public ResponseEntity<Result<TradeManagedOrderPageResponse>> list(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(Result.ok(operationsService.listOrders(state, page, size)));
        } catch (TradeOrderApiException error) {
            return errorResponse(error);
        } catch (DataAccessException | TransactionException unavailable) {
            return ResponseEntity.status(503).body(Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<Result<TradeManagedOrderDetailResponse>> detail(@PathVariable String orderNo) {
        try {
            return ResponseEntity.ok(Result.ok(operationsService.orderDetail(orderNo)));
        } catch (TradeOrderApiException error) {
            return errorResponse(error);
        } catch (DataAccessException | TransactionException unavailable) {
            return ResponseEntity.status(503).body(Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }

    @PostMapping("/{orderNo}/shipment")
    public ResponseEntity<Result<TradeManagedOrderDetailResponse>> ship(
            @RequestHeader(value = "X-User-Id", required = false) String actorId,
            @PathVariable String orderNo, @Valid @RequestBody TradeShipmentRequest request) {
        try {
            return ResponseEntity.ok(Result.ok(operationsService.ship(actorId, orderNo, request)));
        } catch (TradeOrderApiException error) {
            return errorResponse(error);
        } catch (DataAccessException | TransactionException unavailable) {
            return ResponseEntity.status(503).body(Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Object>> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(Result.fail(400, "VALIDATION_FAILED"));
    }

    private static <T> ResponseEntity<Result<T>> errorResponse(TradeOrderApiException error) {
        return ResponseEntity.status(error.status()).body(Result.fail(error.status(), error.errorCode()));
    }
}
