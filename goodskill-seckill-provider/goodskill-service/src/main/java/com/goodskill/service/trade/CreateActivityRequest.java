package com.goodskill.service.trade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateActivityRequest(
        @NotBlank @Size(max = 64) String activityCode,
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid ActivitySku> skus) {

    public record ActivitySku(
            @NotBlank @Size(max = 64) String skuCode,
            @NotBlank @Size(max = 200) String productName,
            @NotBlank @Size(max = 200) String skuName,
            @NotNull @Positive Long unitPriceMinor,
            @NotNull @Positive Integer purchaseLimitPerUser,
            @NotNull @Positive Long allocatedQuantity) {
    }
}
