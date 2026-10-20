package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@Profile("production")
public class TradePaymentCheckoutController {

    private final TradePaymentService paymentService;

    public TradePaymentCheckoutController(TradePaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderNo}/payment")
    public ResponseEntity<Result<TradePaymentCheckoutResponse>> createCheckout(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @PathVariable String orderNo,
            @RequestParam(required = false) String mode) {
        if (buyerId == null || buyerId.isBlank()) {
            return response(HttpStatus.UNAUTHORIZED, Result.fail(401, "UNAUTHENTICATED"));
        }
        try {
            return response(HttpStatus.OK, Result.ok(paymentService.createCheckout(buyerId, orderNo, mode)));
        } catch (TradeOrderApiException error) {
            return response(HttpStatus.valueOf(error.status()), Result.fail(error.status(), error.errorCode()));
        } catch (PaymentChannelException error) {
            return response(HttpStatus.SERVICE_UNAVAILABLE, Result.fail(503, "PAYMENT_PROVIDER_UNAVAILABLE"));
        } catch (DataAccessException | TransactionException unavailable) {
            return response(HttpStatus.SERVICE_UNAVAILABLE, Result.fail(503, "TEMPORARY_UNAVAILABLE"));
        }
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Object>> invalidRequest(Exception ignored) {
        return response(HttpStatus.BAD_REQUEST, Result.fail(400, "VALIDATION_FAILED"));
    }

    private static <T> ResponseEntity<Result<T>> response(HttpStatus status, Result<T> body) {
        return ResponseEntity.status(status).body(body);
    }
}
