package com.goodskill.service.trade;

import com.goodskill.core.info.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestController
@RequestMapping("/activities")
@Profile("production")
@Validated
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<Result<List<ActivityResponse>>> listPublic(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(Result.ok(activityService.publicActivities(limit)));
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<Result<ActivityResponse>> getPublic(@PathVariable long activityId) {
        return ResponseEntity.ok(Result.ok(activityService.publicActivity(activityId)));
    }

    @GetMapping("/manage/{activityId}")
    public ResponseEntity<Result<ActivityResponse>> getForManagement(@PathVariable long activityId) {
        return ResponseEntity.ok(Result.ok(activityService.managementActivity(activityId)));
    }

    @GetMapping("/manage")
    public ResponseEntity<Result<List<ActivityResponse>>> listForManagement(
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(Result.ok(activityService.managementActivities(limit)));
    }

    @PostMapping
    public ResponseEntity<Result<ActivityResponse>> create(
            @RequestHeader(value = "X-User-Id", required = false) String actorId,
            @Valid @RequestBody CreateActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(activityService.create(actorId, request)));
    }

    @PutMapping("/{activityId}")
    public ResponseEntity<Result<ActivityResponse>> updateDraft(
            @PathVariable long activityId,
            @RequestHeader(value = "X-User-Id", required = false) String actorId,
            @Valid @RequestBody UpdateActivityRequest request) {
        return ResponseEntity.ok(Result.ok(activityService.updateDraft(activityId, actorId, request)));
    }

    @PostMapping("/{activityId}/publish")
    public ResponseEntity<Result<ActivityResponse>> publish(
            @PathVariable long activityId,
            @RequestHeader(value = "X-User-Id", required = false) String actorId,
            @Valid @RequestBody ActivityVersionRequest request) {
        return ResponseEntity.ok(Result.ok(
                activityService.publish(activityId, actorId, request.expectedVersion())));
    }

    @PostMapping("/{activityId}/pause")
    public ResponseEntity<Result<ActivityResponse>> pause(
            @PathVariable long activityId,
            @RequestHeader(value = "X-User-Id", required = false) String actorId,
            @Valid @RequestBody ActivityVersionRequest request) {
        return ResponseEntity.ok(Result.ok(
                activityService.pause(activityId, actorId, request.expectedVersion())));
    }

    @ExceptionHandler(ActivityApiException.class)
    public ResponseEntity<Result<ActivityResponse>> activityError(ActivityApiException error) {
        return ResponseEntity.status(error.status())
                .body(Result.fail(error.status(), error.errorCode()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<ActivityResponse>> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(Result.fail(400, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<ActivityResponse>> duplicateResource(DuplicateKeyException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.fail(409, "RESOURCE_CONFLICT"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Result<ActivityResponse>> persistenceUnavailable(RuntimeException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.fail(503, "TEMPORARY_UNAVAILABLE"));
    }
}
